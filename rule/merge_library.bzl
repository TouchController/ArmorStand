load("@rules_kotlin//src/main/starlark/core/compile:common.bzl", _KtJvmInfo = "KtJvmInfo")
load("@rules_java//java:defs.bzl", _JavaInfo = "JavaInfo", _java_common = "java_common")
load("@rules_java//java/bazel/rules:bazel_java_library.bzl", _java_library = "java_library")
load("@rules_kotlin//kotlin:jvm.bzl", _kt_jvm_library = "kt_jvm_library")

def _merge_library_info_init(*, merge_jars = [], deps = []):
    if type(merge_jars) != "depset":
        fail("merge_jars must be type of depset, but it is %s" % type(merge_jars))
    return {
        "merge_jars": merge_jars,
        "transitive_merge_jars": depset(
            transitive = [dep.transitive_merge_jars for dep in deps] + [merge_jars],
        ),
    }

MergeLibraryInfo, _ = provider(
    doc = "A structure for storing libraries to be merged.",
    fields = [
        "merge_jars",
        "transitive_merge_jars",
    ],
    init = _merge_library_info_init,
)

def _modify_deps(deps, associates, merge_deps, plugins, expect, actual):
    real_deps = [dep for dep in deps]
    for merge_dep in merge_deps:
        if not merge_dep in associates:
            real_deps.append(merge_dep)
    real_plugins = [plugin for plugin in plugins]
    if expect or actual:
        real_deps += ["//rule/expect_actual_tools/api:api"]
    if expect:
        real_plugins += ["//rule/expect_actual_tools/processor/java:expect_processor"]
    if actual:
        real_plugins += ["//rule/expect_actual_tools/processor/java:actual_processor"]
    return {"deps": real_deps, "associates": associates, "plugins": real_plugins}

def _merge_library_macro(**kwargs):
    deps = kwargs["deps"] if "deps" in kwargs else []
    associates = kwargs["associates"] if "associates" in kwargs else []
    merge_deps = kwargs["merge_deps"] if "merge_deps" in kwargs else []
    plugins = kwargs["plugins"] if "plugins" in kwargs else []
    expect = kwargs["expect"] if "expect" in kwargs else False
    actual = kwargs["actual"] if "actual" in kwargs else False
    return kwargs | _modify_deps(deps, associates, merge_deps, plugins, expect, actual)

def _java_merge_library_import_impl(ctx):
    return [
        MergeLibraryInfo(
            merge_jars = ctx.attr.src[_JavaInfo].full_compile_jars,
            deps = [dep[MergeLibraryInfo] for dep in ctx.attr.deps],
        ),
        ctx.attr.src[_JavaInfo]
    ]

java_merge_library_import = rule(
    implementation = _java_merge_library_import_impl,
    attrs = {
        "src": attr.label(
            providers = [_JavaInfo]
        ),
        "deps": attr.label_list(
            providers = [MergeLibraryInfo]
        ),
    }
)

def _java_merge_library_impl(ctx):
    target = ctx.super()
    java_info = target[0]
    return [
        MergeLibraryInfo(
            merge_jars = java_info.full_compile_jars,
            deps = [dep[MergeLibraryInfo] for dep in ctx.attr.merge_deps],
        ),
    ] + target

java_merge_library = rule(
    parent = _java_library,
    implementation = _merge_library_macro,
    initializer = _java_merge_library_impl,
    attrs = {
        "merge_deps": attr.label_list(
            providers = [MergeLibraryInfo]
        ),
        "expect": attr.bool(
            mandatory = False,
            default = False,
        ),
        "actual": attr.bool(
            mandatory = False,
            default = False,
        ),
    },
)

def _kt_merge_library_import_impl(ctx):
    return [
        MergeLibraryInfo(
            merge_jars = ctx.attr.src[_JavaInfo].full_compile_jars,
            deps = [dep[MergeLibraryInfo] for dep in ctx.attr.deps],
        ),
        ctx.attr.src[_JavaInfo],
        ctx.attr.src[_KtJvmInfo]
   ]

kt_merge_library_import = rule(
    implementation = _kt_merge_library_import_impl,
    attrs = {
        "src": attr.label(
            providers = [_JavaInfo, _KtJvmInfo]
        ),
        "deps": attr.label_list(
            providers = [MergeLibraryInfo]
        ),
    }
)

def _kt_merge_library_impl(ctx):
    target = ctx.super()
    kt_info = target[1]
    return [
        MergeLibraryInfo(
            merge_jars = depset(kt_info.all_output_jars),
            deps = [dep[MergeLibraryInfo] for dep in ctx.attr.merge_deps],
        )
    ] + target

kt_merge_library = rule(
    parent = _kt_jvm_library,
    initializer = _merge_library_macro,
    implementation = _kt_merge_library_impl,
    attrs = {
        "merge_deps": attr.label_list(
            providers = [MergeLibraryInfo]
        ),
        "expect": attr.bool(
            mandatory = False,
            default = False,
        ),
        "actual": attr.bool(
            mandatory = False,
            default = False,
        ),
    },
)

def _merge_library_jar_impl(ctx):
    output_jar = ctx.actions.declare_file(ctx.label.name + ".jar")

    merged_deps_depset = depset(transitive = [dep[MergeLibraryInfo].transitive_merge_jars for dep in ctx.attr.deps])
    merged_deps = merged_deps_depset.to_list()

    args = ctx.actions.args()
    args.add(output_jar)
    args.add_all(merged_deps)
    resource_files = []
    for resource in ctx.attr.resources.keys():
        strip = ctx.attr.resources[resource]
        files = resource.files.to_list()
        resource_files = resource_files + files
        args.add("--strip")
        args.add(strip)
        if len(files) == 0:
            fail("Resource label without resource: " + str(resource.label))
        for file in files:
            args.add("--resource")
            args.add(file)

    ctx.actions.run(
        inputs = depset(
            direct = resource_files,
            transitive = [merged_deps_depset],
        ),
        outputs = [output_jar],
        executable = ctx.executable._merge_jar_executable,
        arguments = [args],
        progress_message = "Merging JAR %s" % ctx.label.name,
        toolchain = "@bazel_tools//tools/jdk:toolchain_type",
    )

    return [
        JavaInfo(
            output_jar = output_jar,
            compile_jar = output_jar,
        ),
        DefaultInfo(files = depset([output_jar])),
    ]

merge_library_jar = rule(
    implementation = _merge_library_jar_impl,
    attrs = {
        "deps": attr.label_list(
            mandatory = True,
            providers = [MergeLibraryInfo],
            doc = "Input libraries to be merged",
        ),
        "resources": attr.label_keyed_string_dict(
            mandatory = False,
            allow_files = True,
            default = {},
            doc = "Resource to be merged, with perfix to strip",
        ),
        "_merge_jar_executable": attr.label(
            default = Label("@//rule/merge_expect_actual_jar"),
            executable = True,
            cfg = "exec",
        ),
    },
    doc = "Merge libraries into a single JAR"
)
