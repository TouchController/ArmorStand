# Force rebuild 2025-11-29 v2

load("@rules_java//java:defs.bzl", "java_common")
load("@rules_java//java/common:java_info.bzl", "JavaInfo")

def _remap_jar_impl(ctx):
    output_jars = []
    java_infos = []

    # Collect classpath dependencies
    classpath_depsets = []
    for item in ctx.attr.classpath:
        if JavaInfo in item:
            classpath_depsets.append(item[JavaInfo].full_compile_jars)
        else:
            classpath_depsets.append(item[DefaultInfo].files)

    # Collect all input JARs
    input_jar_depsets = []
    for input_target in ctx.attr.inputs:
        input_java_info = input_target[JavaInfo]
        classpath_depsets.append(input_java_info.transitive_compile_time_jars)
        if ctx.attr.remap_transitive_deps:
            input_jar_depsets.append(input_java_info.full_compile_jars)
            input_jar_depsets.append(input_java_info.transitive_compile_time_jars)
        else:
            input_jar_depsets.append(depset(input_java_info.runtime_output_jars))

    classpath = depset(transitive = classpath_depsets)

    input_jars = depset(transitive = input_jar_depsets)
    for input_jar in input_jars.to_list():
        if len(input_jar.basename) > 8 and input_jar.basename[-8:] == "ijar.jar":
            continue

        if ctx.attr.remap_transitive_deps:
            # Generate unique output name per input
            output_jar = ctx.actions.declare_file("_remapped/%s_%s" % (ctx.label.name, input_jar.basename))
        else:
            output_jar = ctx.actions.declare_file("_remapped/%s.jar" % ctx.label.name)
        output_jars.append(output_jar)

        # Generate arguments for Tiny Remapper
        args = ctx.actions.args()
        if ctx.attr.mixin:
            args.add("--mixin")
        if ctx.attr.fix_package_access:
            args.add("--fix_package_access")
        if ctx.attr.remap_access_widener:
            args.add("--remap_access_widener")
            if ctx.attr.access_widener_from_namespace:
                args.add("--access_widener_from_namespace_" + ctx.attr.access_widener_from_namespace)
        if ctx.attr.remove_jar_in_jar:
            args.add("--remove_jar_in_jar")

        args.add_all([
            input_jar.path,
            output_jar.path,
            ctx.file.mapping.path,
            ctx.attr.from_namespace,
            ctx.attr.to_namespace,
        ])

        # Add classpath entries to remapper args
        for classpath_item in classpath.to_list():
            args.add(classpath_item.path)

        # Collect all inputs needed for this action
        inputs = depset(
            [input_jar, ctx.file.mapping],
            transitive = [classpath],
        )

        args.use_param_file("@%s", use_always = True)

        ctx.actions.run(
            inputs = inputs,
            outputs = [output_jar],
            executable = ctx.executable._tiny_remapper_bin,
            execution_requirements = {
                "supports-workers": "1",
                "requires-worker-protocol": "json",
            },
            arguments = [args],
            progress_message = "Remapping %s - %s" % (ctx.label.name, input_jar.basename),
        )

        # Create JavaInfo for each remapped JAR
        java_infos.append(JavaInfo(
            output_jar = output_jar,
            compile_jar = output_jar,
        ))

    # Merge all JavaInfos from individual outputs
    merged_java_info = java_common.merge(java_infos)

    return [
        merged_java_info,
        DefaultInfo(files = depset(output_jars)),
    ]

remap_jar = rule(
    implementation = _remap_jar_impl,
    attrs = {
        "inputs": attr.label_list(
            providers = [JavaInfo],
            mandatory = True,
            doc = "Input JAR files. Each should be a label with JavaInfo",
        ),
        "mapping": attr.label(
            allow_single_file = [".tiny"],
            mandatory = True,
            doc = "Mapping file. Must be .tiny file",
        ),
        "remap_transitive_deps": attr.bool(
            default = False,
            doc = "Remap transitive dependencies",
        ),
        "classpath": attr.label_list(
            allow_empty = True,
            doc = "Classpath for remapping",
        ),
        "from_namespace": attr.string(
            mandatory = True,
            doc = "Map from this namespace",
        ),
        "to_namespace": attr.string(
            mandatory = True,
            doc = "Map to this namespace",
        ),
        "mixin": attr.bool(
            default = False,
            doc = "Handle mixin mappings",
        ),
        "fix_package_access": attr.bool(
            default = False,
            doc = "Fix invalid package access",
        ),
        "remap_access_widener": attr.bool(
            default = True,
            doc = "Remap access wideners",
        ),
        "access_widener_from_namespace": attr.string(
            mandatory = False,
            default = "",
            doc = "Map access wideners from this namespace",
        ),
        "remove_jar_in_jar": attr.bool(
            default = False,
            doc = "Remove Jar-IN-Jar for Fabric mods`",
        ),
        "_tiny_remapper_bin": attr.label(
            default = Label("//rule/tiny_remapper_worker"),
            executable = True,
            cfg = "exec",
        ),
    },
    doc = "Remaps multiple JARs using a tiny mapping, processing each JAR individually.",
)
