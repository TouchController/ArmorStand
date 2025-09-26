load("@rules_java//java:defs.bzl", "java_library")
load("@bazel_skylib//rules:expand_template.bzl", "expand_template")

def _mod_info_jar_impl(name, visibility, fabric, neoforge, resource_strip_prefix, substitutions):
    resources = []
    if fabric:
        fabric_name = name + "_fabric_expanded"
        expand_template(
            name = fabric_name,
            template = fabric,
            substitutions = substitutions,
            out = "fabric.mod.json",
        )
        resources.append(fabric_name)
    if neoforge:
        neoforge_name = name + "_neoforge_expanded"
        mod_id = substitutions["${mod_id}"]
        if mod_id:
            substitutions = substitutions | { "${mod_id}": mod_id.replace("-", "_") }
        print(substitutions)
        expand_template(
            name = neoforge_name,
            template = neoforge,
            substitutions = substitutions,
            out = "META-INF/neoforge.mods.toml",
        )
        resources.append(neoforge_name)

    java_library(
        name = name,
        visibility = visibility,
        resources = resources,
        resource_strip_prefix = resource_strip_prefix,
    )

mod_info_jar = macro(
    attrs = {
        "fabric": attr.label(
            mandatory = False,
            allow_single_file = [".json"],
            doc = "Input fabric.mod.json file",
        ),
        "neoforge": attr.label(
            mandatory = False,
            allow_single_file = [".toml"],
            doc = "Input neoforge.mods.toml file",
        ),
        "resource_strip_prefix": attr.string(
            mandatory = True,
        ),
        "substitutions": attr.string_dict(
            mandatory = True,
            configurable = False,
            doc = "A dictionary mapping strings to their substitutions.",
        ),
    },
    implementation = _mod_info_jar_impl,
)