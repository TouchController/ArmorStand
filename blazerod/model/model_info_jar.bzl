load("//rule:mod_info_jar.bzl", "mod_info_jar")
load("//:properties.bzl", "blazerod_version", "issue_tracker", "license", "home_page", "sources_page")

def _model_info_jar_impl(name, visibility, substitutions):
    predefined_substitutions = {
        "${version}": blazerod_version,
        "${license}": license,
        "${home_page}": home_page,
        "${sources_page}": sources_page,
        "${issue_tracker}": issue_tracker,
    }
    predefined_substitutions.update(substitutions)
    mod_info_jar(
        name = name,
        visibility = visibility,
        fabric = "//blazerod/model:src/main/resources/fabric.mod.json",
        neoforge = "//blazerod/model:src/main/resources/META-INF/neoforge.mods.toml",
        resource_strip_prefix = native.package_name(),
        substitutions = predefined_substitutions,
    )

model_info_jar = macro(
    attrs = {
        "substitutions": attr.string_dict(
            mandatory = True,
            configurable = False,
            doc = "A dictionary mapping strings to their substitutions.",
        ),
    },
    implementation = _model_info_jar_impl,
)