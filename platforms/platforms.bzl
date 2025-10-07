_CPUS = [
    "aarch32",
    "aarch64",
    "armv6-m",
    "armv7-m",
    "armv7e-m",
    "armv7e-mf",
    "armv8-m",
    "arm64_32",
    "arm64e",
    "armv7",
    "armv7k",
    "cortex-r52",
    "i386",
    "ppc",
    "ppc32",
    "ppc64le",
    "s390x",
    "x86_32",
    "x86_64",
    "wasm32",
    "wasm64",
    "mips64",
    "riscv32",
    "riscv64",
]

_OSES = [
    "freebsd",
    "netbsd",
    "openbsd",
    "haiku",
    "android",
    "linux",
    "windows",
    "vxworks",
    "ios",
    "macos",
    "tvos",
    "watchos",
    "visionos",
    "qnx",
    "nixos",
    "emscripten",
    "wasi",
    "fuchsia",
    "chromiumos",
    "uefi",
]

select_current_cpu = select({
    "@platforms//cpu:%s" % cpu: cpu
    for cpu in _CPUS
})

select_current_os = select({
    "@platforms//os:%s" % os: os
    for os in _OSES
})
