load("@rules_cc//cc:defs.bzl", "cc_library", "cc_shared_library")
load("@rules_java//java:defs.bzl", "java_library")
load("//platforms:platforms.bzl", "select_current_cpu", "select_current_os")
load("@bazel_lib//lib:copy_file.bzl", "copy_file_action", "COPY_FILE_TOOLCHAINS")

# Most logic come from rules_jni

_command_line_platforms = "//command_line_option:platforms"

def _multi_platform_transition_impl(settings, attrs):
    if not attrs.platforms:
        return {
            _command_line_platforms: settings[_command_line_platforms],
        }
    return [
        {_command_line_platforms: [target_platform]}
        for target_platform in attrs.platforms
    ]

_multi_platform_transition = transition(
    implementation = _multi_platform_transition_impl,
    inputs = [_command_line_platforms],
    outputs = [_command_line_platforms],
)

JniLibraryInfo = provider(fields = ["file", "os", "cpu", "platform"])

def _jni_library_info_impl(ctx):
    lib_files = ctx.attr.lib[DefaultInfo].files.to_list()
    if len(lib_files) > 1:
        fail("Expected exactly one file in lib target: %s" % lib_files)
    lib_file = lib_files[0]

    return [
        JniLibraryInfo(
            file = lib_file,
            os = ctx.attr.os,
            cpu = ctx.attr.cpu,
            platform = ctx.fragments.platform.platform,
        ),
    ]

_jni_library_info = rule(
    implementation = _jni_library_info_impl,
    attrs = {
        "lib": attr.label(mandatory = True),
        "os": attr.string(mandatory = True),
        "cpu": attr.string(mandatory = True),
    },
    provides = [JniLibraryInfo],
    fragments = ["platform"],
)

def _jni_library_collector_impl(ctx):
    output_files = []
    seen_platforms = {}

    for info_provider in ctx.attr.infos:
        info = info_provider[JniLibraryInfo]
        os = info.os
        cpu = info.cpu
        original_name = ctx.attr.original_name

        identifier = "{}_{}".format(os, cpu)
        if identifier in seen_platforms:
            fail("""Target '{name}' is produced by multiple platforms for OS '{os}' and CPU '{cpu}':
    Platform 1: {p1}
    Platform 2: {p2}
Ensure each (os, cpu) pair is unique.""".format(
                name = original_name,
                os = os,
                cpu = cpu,
                p1 = seen_platforms[identifier],
                p2 = info.platform,
            ))
        seen_platforms[identifier] = info.platform

        lib_basename = info.file.basename
        lib_extension = lib_basename.split(".")[-1]

        output_path = "{original_name}_{os}_{cpu}/lib{lib_basename}.{lib_extension}".format(
            original_name = original_name,
            os = os,
            cpu = cpu,
            lib_basename = original_name,
            lib_extension = lib_extension,
        )
        output_file = ctx.actions.declare_file(output_path)

        copy_file_action(
            ctx,
            info.file,
            output_file,
        )
        output_files.append(output_file)

    return [DefaultInfo(files = depset(output_files))]

_jni_library_collector = rule(
    implementation = _jni_library_collector_impl,
    toolchains = COPY_FILE_TOOLCHAINS,
    attrs = {
        "infos": attr.label_list(
            providers = [JniLibraryInfo],
            cfg = _multi_platform_transition,
            mandatory = True,
        ),
        "original_name": attr.string(mandatory = True),
        "platforms": attr.label_list(),
        "_allowlist_function_transition": attr.label(
            default = "@bazel_tools//tools/allowlists/function_transition_allowlist",
        ),
    },
)

def cc_jni_library(name, platforms = [], **kwargs):
    cc_lib_name = name + "_cc_lib_private"
    info_name = name + "_info_private"
    collector_name = name + "_collector_private"

    tags = kwargs.pop("tags", default = None)
    visibility = kwargs.pop("visibility", default = None)

    cc_shared_library(
        name = cc_lib_name,
        visibility = ["//visibility:private"],
        tags = ["manual"],
        **kwargs,
    )

    _jni_library_info(
        name = info_name,
        visibility = ["//visibility:private"],
        tags = ["manual"],
        lib = ":" + cc_lib_name,
        os = select_current_os,
        cpu = select_current_cpu,
    )

    _jni_library_collector(
        name = collector_name,
        visibility = ["//visibility:private"],
        tags = ["manual"],
        infos = [":" + info_name],
        original_name = name,
        platforms = platforms,
    )

    java_library(
        name = name,
        tags = tags,
        visibility = visibility,
        resources = [":" + collector_name],
        resource_strip_prefix = native.package_name(),
    )
