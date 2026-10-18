#!/usr/bin/env python3
# summarize.py <segment dir> [target fps]: frame pacing from the SurfaceFlinger samples, the guest frame rate
# the core logs (GpuFrame), power, clocks, temperatures and the busiest threads of one segment written by run.sh.
import csv
import os
import statistics
import sys

d = sys.argv[1]
target = float(sys.argv[2]) if len(sys.argv) > 2 else 30.0


def presents():
    seen = set()
    try:
        rows = open(os.path.join(d, "sf_latency.txt")).read().splitlines()
    except FileNotFoundError:
        return []
    for r in rows:
        f = r.split()
        if len(f) != 3:
            continue
        actual = int(f[1])
        if 0 < actual < 2**62:
            seen.add(actual)
    return sorted(seen)


def frame_stats(ts):
    if len(ts) < 3:
        return "fps n/a (no SurfaceFlinger samples: wrong layer or nothing presented)"
    gaps = [(b - a) / 1e6 for a, b in zip(ts, ts[1:])]
    # A 1 s sampling hole longer than SurfaceFlinger's 128-frame window would read as one long frame
    gaps = [g for g in gaps if g < 1000]
    span = sum(gaps) / 1000
    fps = len(gaps) / span if span else 0
    budget = 1000 / target
    slow = sum(1 for g in gaps if g > budget * 1.5)
    worst = sorted(gaps)[-max(1, len(gaps) // 100):]
    return (f"fps {fps:.1f} (target {target:.0f}), slow frames {100 * slow / len(gaps):.1f}% "
            f"(>{budget * 1.5:.0f} ms), 1% worst {statistics.mean(worst):.0f} ms, "
            f"median {statistics.median(gaps):.1f} ms over {span:.0f} s")


def power():
    try:
        rows = list(csv.DictReader(open(os.path.join(d, "poll.csv"))))
    except FileNotFoundError:
        return "no poll.csv"
    rows = [r for r in rows if r.get("usb_mv") not in (None, "")]
    if not rows:
        return "no poll rows"

    def mean(k):
        return statistics.mean(float(r[k]) for r in rows)

    batt_w = statistics.mean(-float(r["batt_ma"]) * float(r["batt_mv"]) / 1e6 for r in rows)
    usb_w = statistics.mean(float(r["usb_ma"]) * float(r["usb_mv"]) / 1e6 for r in rows)
    on_usb = usb_w > 0.5
    # On the charger the battery receives what the device leaves: device draw = USB in - battery charge
    w = usb_w + batt_w if on_usb else batt_w
    src = "usb-batt" if on_usb else "battery"
    return (f"power {abs(w):.2f} W ({src}), gpu {mean('gpu_busy'):.0f}% @ {mean('gpu_mhz'):.0f} MHz, "
            f"cpu0 {mean('c0_mhz'):.0f} MHz, prime {mean('c6_mhz'):.0f} MHz, "
            f"cpu max {max(float(r['cpu_tmax']) for r in rows) / 1000:.0f} C, "
            f"gpu max {max(float(r['gpu_tmax']) for r in rows) / 1000:.0f} C, "
            f"board max {max(float(r['sys_tmax']) for r in rows) / 1000:.1f} C, "
            f"battery {float(rows[0]['batt_t']) / 10:.1f}->{float(rows[-1]['batt_t']) / 10:.1f} C")


def threads():
    def load(name):
        out = {}
        try:
            for line in open(os.path.join(d, name)):
                head, _, rest = line.partition(") ")
                if not rest:
                    continue
                tid, _, comm = head.partition(" (")
                f = rest.split()
                out[tid] = (comm, int(f[11]) + int(f[12]))
        except FileNotFoundError:
            pass
        return out

    a, b = load("threads_start.txt"), load("threads_end.txt")
    try:
        secs = float(open(os.path.join(d, "duration")).read())
    except (FileNotFoundError, ValueError):
        return ""
    busy = sorted(((b[t][1] - a[t][1], b[t][0]) for t in b if t in a), reverse=True)[:6]
    return "threads " + ", ".join(f"{c} {100 * j / 100 / secs:.0f}%" for j, c in busy if j > 0)


def core():
    # The command processor's once-a-second lines (log_gpu_frame_time_breakdown): guest frames (swaps) per
    # second and where the GPU thread's frame went, and the host GPU's busy time per submission
    import re
    gf, vk = {}, {}
    try:
        for line in open(os.path.join(d, "core.log"), errors="replace"):
            if "GpuFrame:" in line:
                m = re.search(r"GpuFrame: (\d+) frames", line)
                gf.setdefault("fps", []).append(float(m.group(1)))
                for k in ("exec", "draw", "swap", "stall", "waited"):
                    m = re.search(k + r"=([\d.]+)ms", line)
                    if m:
                        gf.setdefault(k, []).append(float(m.group(1)))
                m = re.search(r"draws=(\d+)", line)
                if m:
                    gf.setdefault("draws", []).append(float(m.group(1)))
            elif "VkFrameSync:" in line:
                for k, pat in (("gpu", r"gpu exec avg=([\d.]+)ms"), ("gap", r"gap avg=([\d.]+)ms"),
                               ("await", r"await=([\d.]+)ms"), ("subs", r"submissions=([\d.]+)")):
                    m = re.search(pat, line)
                    if m:
                        vk.setdefault(k, []).append(float(m.group(1)))
    except FileNotFoundError:
        return "", ""
    if not gf:
        return "", ""
    mean = lambda v: statistics.mean(v) if v else 0
    g = f", guest {mean(gf['fps']):.1f} fps (min {min(gf['fps']):.0f})"
    detail = ("gpu thread ms/frame " + " ".join(f"{k} {mean(gf[k]):.1f}" for k in ("exec", "draw", "swap", "stall", "waited") if k in gf)
              + f", draws/frame {mean(gf.get('draws', [])):.0f}")
    if vk:
        detail += ", host gpu " + " ".join(f"{k} {mean(v):.1f}" for k, v in vk.items())
    return g, detail


g, detail = core()
print(f"[{os.path.basename(os.path.normpath(d))}] {frame_stats(presents())}{g}")
print(f"  {power()}")
t = threads()
if t:
    print(f"  {t}")
if detail:
    print(f"  {detail}")
