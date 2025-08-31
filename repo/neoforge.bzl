"""NeoForge repository rule, fetches NeoForge artifact and setup Bazel rules"""

load("@//private:maven_coordinate.bzl", _convert_maven_coordinate = "convert_maven_coordinate", _convert_maven_coordinate_to_repo = "convert_maven_coordinate_to_repo", _convert_maven_coordinate_to_url = "convert_maven_coordinate_to_url")
load("@//private:snake_case.bzl", _camel_case_to_snake_case = "camel_case_to_snake_case")
load("@//private:pin_file.bzl", _parse_pin_file = "parse_pin_file")
load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_jar")

_neoforge_repository_url = "https://maven.neoforged.net/releases"
_mojang_repository_url = "https://libraries.minecraft.net"

def _convert_maven_coordinate_to_url_with_repo(maven_coordinate, extension = "jar"):
    # Ugly but works
    if "mojang" in maven_coordinate:
        return _convert_maven_coordinate_to_url(_mojang_repository_url, maven_coordinate, extension)
    else:
        return _convert_maven_coordinate_to_url(_neoforge_repository_url, maven_coordinate, extension)

def _neoforge_repo_impl(rctx):
    version_name = rctx.attr.version
    version_userdev_sha256 = rctx.attr.userdev_sha256
    version_universal_sha256 = rctx.attr.universal_sha256
    version_sources_sha256 = rctx.attr.sources_sha256

    neoforge_userdev_zip = "neoforge.zip"
    neoforge_universal_zip = "neoforge_unversal.jar"
    neoforge_sources_zip = "neoforge_sources.srcjar"
    output_prefix = "neoforge/%s" % version_name

    rctx.report_progress("Downloading NeoForm JAR %s" % version_name)
    rctx.download(
        url = "%s/net/neoforged/neoforge/%s/neoforge-%s-userdev.jar" % (_neoforge_repository_url, version_name, version_name),
        sha256 = version_userdev_sha256,
        output = neoforge_userdev_zip,
    )
    rctx.extract(
        archive = neoforge_userdev_zip,
        output = output_prefix,
    )

    config_data = json.decode(rctx.read("%s/config.json" % output_prefix))
    download_tokens = [
        rctx.download(
            url = _convert_maven_coordinate_to_url(_neoforge_repository_url, config_data["sources"]),
            sha256 = version_sources_sha256,
            output = neoforge_sources_zip,
            block = False,
        ),
        rctx.download(
            url = _convert_maven_coordinate_to_url(_neoforge_repository_url, config_data["universal"]),
            sha256 = version_universal_sha256,
            output = neoforge_universal_zip,
            block = False,
        ),
    ]
    for token in download_tokens:
        token.wait()

    neoforge_libraries = []
    for library in config_data["libraries"]:
        neoforge_libraries.append('"@%s//jar"' % _convert_maven_coordinate_to_repo("neoforge", library))
    neoforge_libraries = ", ".join(neoforge_libraries)

    build_file_contents = [
        'package(default_visibility = ["//visibility:public"])',
        'load("@//repo/neoforge/rule:java_source_transform.bzl", "java_source_transform")',
        'load("@//repo/neoforge/rule:remove_manifest.bzl", "remove_manifest")',
        'load("@//repo/neoform/rule:patch_zip_content.bzl", "patch_zip_content")',
        'load("@//repo/neoform/rule:import_source_info.bzl", "import_source_info")',
        'load("@//repo/neoform/rule:inject_zip_content.bzl", "inject_zip_content")',
        'load("@rules_java//java:defs.bzl", "java_library", "java_import")',
        '',
        'alias(',
        '    name = "neoforge_userdev",',
        '    actual = "%s",' % neoforge_userdev_zip,
        ')',
        'java_import(',
        '    name = "neoforge_universal",',
        '    jars = ["%s"],' % neoforge_universal_zip,
        '    srcjar = "%s",' % neoforge_sources_zip,
        ')',
        'alias(',
        '    name = "neoforge_sources",',
        '    actual = "%s",' % neoforge_sources_zip,
        ')',
        '',
        'patch_zip_content(',
        '    name = "add_neoforge_patches",',
        '    prefix = "%s",' % config_data["patches"],
        '    patches = ":neoforge_userdev",',
        '    input = "%s",' % rctx.attr.joined_patched_sources,
        ')',
        '',
        'java_source_transform(',
        '    name = "transform_sources",',
        '    input = ":add_neoforge_patches",',
        '    access_transformers = glob(["%s/%s*"]),' % (output_prefix, config_data["ats"]),
        ')',
        '',
        'import_source_info(',
        '    name = "decompile_libraries",',
        '    deps = [":transform_sources"],',
        ')',
        '',
        'java_library(',
        '    name = "recompile_with_manifest",',
        '    srcs = [":transform_sources"],',
        '    deps = [',
        '        ":decompile_libraries",',
        '        ":neoforge_universal",',
        '        %s' % neoforge_libraries,
        '    ],',
        '    javacopts = ["-XepDisableAllChecks", "-nowarn", "-g", "-proc:none", "-implicit:none"],',
        ')',
        '',
        'remove_manifest(',
        '    name = "recompile",',
        '    src = ":recompile_with_manifest",',
        ')',
        'java_import(',
        '    name = "recompile_with_deps",',
        '    jars = [":recompile"],',
        '    srcjar = ":transform_sources",',
        ')',
        '',
        'inject_zip_content(',
        '    name = "sources_with_neoforge",',
        '    input = ":transform_sources",',
        '    deps = [":neoforge_sources"],',
        ')',
        '',
        'inject_zip_content(',
        '    name = "compiled_with_neoforge",',
        '    input = ":recompile",',
        '    deps = [":neoforge_universal"],',
        ')',
        'java_import(',
        '    name = "compiled_with_neoforge_with_deps",',
        '    jars = [":compiled_with_neoforge"],',
        '    srcjar = ":sources_with_neoforge",',
        '    deps = [',
        '        ":decompile_libraries",',
        '        %s' % neoforge_libraries,
        '    ],',
        ')',
    ]

    rctx.file("BUILD.bazel", "\n".join(build_file_contents))

_neoforge_repo = repository_rule(
    implementation = _neoforge_repo_impl,
    attrs = {
        "version": attr.string(
            doc = "Version of NeoForge",
            mandatory = True,
        ),
        "userdev_sha256": attr.string(
            doc = "SHA-256 of the NeoForge userdev JAR file",
            mandatory = True,
        ),
        "universal_sha256": attr.string(
            doc = "SHA-256 of the NeoForge universal JAR file",
            mandatory = True,
        ),
        "sources_sha256": attr.string(
            doc = "SHA-256 of the NeoForge sources JAR file",
            mandatory = True,
        ),
        "joined_patched_sources": attr.label(
            doc = "Joined patched sources, usually come from NeoForm",
            allow_single_file = [".jar"],
            mandatory = True,
        ),
    },
)

def _neoforge_pin_impl(rctx):
    url_lines = []
    for coordinate in rctx.attr.libraries:
        url_lines.append('"%s"' % _convert_maven_coordinate_to_url_with_repo(coordinate))
    rctx.template("PinGenerator.java", rctx.attr._pinner_source, {
        "/*INJECT HERE*/": ", ".join(url_lines),
        "/*OUTPUT NAME*/": "neoforge_pin"
    })

    build_bazel_contents = [
        'load("@rules_java//java:defs.bzl", "java_binary")',
        'package(default_visibility = ["//visibility:public"])',
        "",
        "java_binary(",
        '    name = "pin",',
        '    srcs = ["PinGenerator.java"],',
        '    main_class = "PinGenerator",',
        ")",
    ]
    rctx.file("BUILD.bazel", "\n".join(build_bazel_contents))

neoforge_pin = repository_rule(
    implementation = _neoforge_pin_impl,
    attrs = {
        "libraries": attr.string_list(
            doc = "List of library coordinates to pin",
        ),
        "_pinner_source": attr.label(
            allow_single_file = [".java"],
            default = "@//repo/neoform/pin_generator:PinGenerator.java",
        ),
    },
)

version = tag_class(
    attrs = {
        "version": attr.string(
            doc = "Version of NeoForge",
            mandatory = True,
        ),
        "userdev_sha256": attr.string(
            doc = "SHA-256 of the NeoForge userdev JAR file",
            mandatory = True,
        ),
        "universal_sha256": attr.string(
            doc = "SHA-256 of the NeoForge universal JAR file",
            mandatory = True,
        ),
        "sources_sha256": attr.string(
            doc = "SHA-256 of the NeoForge sources JAR file",
            mandatory = True,
        ),
        "joined_patched_sources": attr.label(
            doc = "Joined patched sources, usually come from NeoForm",
            allow_single_file = [".jar"],
            mandatory = True,
        ),
    },
)

pin = tag_class(
    attrs = {
        "pin_file": attr.label(
            doc = "Pin file",
            allow_single_file = [".txt"],
            mandatory = False,
        ),
    },
)

def _neoforge_impl(mctx):
    versions = {}
    pin_file = None
    for module in mctx.modules:
        for pin in module.tags.pin:
            if pin_file != None:
                fail("Multiple pins found")
            else:
                pin_file = pin.pin_file
        for version in module.tags.version:
            if version in versions:
                if versions[version.version].userdev_sha256 != version.userdev_sha256:
                    fail("NeoForm version %s already exists with a different userdev SHA-256" % version.version)
                elif versions[version.version].universal_sha256 != version.universal_sha256:
                    fail("NeoForm version %s already exists with a different universal SHA-256" % version.version)
                elif versions[version.version].sources_sha256 != version.sources_sha256:
                    fail("NeoForm version %s already exists with a different sources SHA-256" % version.version)
                elif versions[version.version].joined_patched_sources != version.joined_patched_sources:
                    fail("NeoForm version %s already exists with a different joined patched sources" % version.version)
            else:
                versions[version.version] = {
                    "version": version.version,
                    "userdev_sha256": version.userdev_sha256,
                    "universal_sha256": version.universal_sha256,
                    "sources_sha256": version.sources_sha256,
                    "joined_patched_sources": version.joined_patched_sources,
                }
    versions = versions.values()

    libraries = []
    def append_library(coordinate):
        if coordinate not in libraries:
            libraries.append(coordinate)

    for version in versions:
        version_name = version["version"]
        version_userdev_sha256 = version["userdev_sha256"]
        output_prefix = "neoforge/%s" % version_name

        mctx.report_progress("Downloading NeoForm JAR %s" % version_name)
        mctx.download_and_extract(
            url = "%s/net/neoforged/neoforge/%s/neoforge-%s-userdev.jar" % (_neoforge_repository_url, version_name, version_name),
            type = "zip",
            sha256 = version_userdev_sha256,
            output = output_prefix,
        )

        repo_name = _convert_maven_coordinate_to_repo("neoforge", version_name)
        _neoforge_repo(
            name = repo_name,
            version = version_name,
            userdev_sha256 = version_userdev_sha256,
            universal_sha256 = version["universal_sha256"],
            sources_sha256 = version["sources_sha256"],
            joined_patched_sources = version["joined_patched_sources"],
        )

        config_data = json.decode(mctx.read("%s/config.json" % output_prefix))
        for library in config_data["libraries"]:
            append_library(library)

    pin_content = {}
    if pin_file != None:
        pin_content = _parse_pin_file(mctx.read(pin_file))
    for library in libraries:
        http_jar(
            name = _convert_maven_coordinate_to_repo("neoforge", library),
            url = _convert_maven_coordinate_to_url_with_repo(library),
            sha256 = pin_content.get(_convert_maven_coordinate_to_url_with_repo(library), None),
        )

    neoforge_pin(
        name = "neoforge_pin",
        libraries = libraries,
    )

neoforge = module_extension(
    implementation = _neoforge_impl,
    tag_classes = {
        "version": version,
        "pin": pin,
    },
)
