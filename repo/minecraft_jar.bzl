load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_file")
load("//private:bytes_util.bzl", "hex_sha1_to_sri")

minecraft_jar = tag_class(
    attrs = {
        "version": attr.string(
            doc = "The Minecraft version to be used",
        ),
        "type": attr.string(
            doc = "The type of JAR or mappings you want to use.",
            values = ["server", "client"],
            default = "client",
        ),
        "mapping": attr.bool(
            doc = "Download mappings",
            default = False,
        ),
        "assets": attr.bool(
            doc = "Download assets",
            default = False,
        ),
    },
)

exclude_library = tag_class(
    attrs = {
        "names": attr.string_list(
            doc = "Names to exclude",
            default = [],
        )
    },
)

def _minecraft_repo_impl(rctx):
    version_repo_names = rctx.attr.version_repo_names
    version_libraries = rctx.attr.version_libraries

    build_content = [
        'load("@rules_java//java:defs.bzl", "java_import")',
        'package(default_visibility = ["//visibility:public"])',
        "",
    ]

    for version_repo in version_repo_names:
        actual_repo = "@minecraft_%s//file" % version_repo
        if version_repo.endswith("mapping"):
            build_content.append("alias(")
            build_content.append('    name = "%s",' % version_repo)
            build_content.append('    actual = "%s",' % actual_repo)
            build_content.append(")")
        else:
            build_content.append("java_import(")
            build_content.append('    name = "%s",' % version_repo)
            build_content.append('    jars = ["%s"],' % actual_repo)
            build_content.append('    deps = ["%s_libraries"]' % version_repo)
            build_content.append(")")
            build_content.append("java_import(")
            build_content.append('    name = "%s_libraries",' % version_repo)
            build_content.append("    jars = [")
            build_content.append("        %s" % version_libraries[version_repo])
            build_content.append("    ],")
            build_content.append(")")
        build_content.append("")

    rctx.file(
        "BUILD.bazel",
        content = "\n".join(build_content),
    )

    return None

minecraft_repo = repository_rule(
    implementation = _minecraft_repo_impl,
    attrs = {
        "version_repo_names": attr.string_list(),
        "version_libraries": attr.string_dict(),
        "version_assets": attr.string_dict(),
    },
)

def _minecraft_assets_repo_impl(rctx):
    asset_objects = rctx.attr.asset_objects
    asset_manifests = rctx.attr.asset_manifests
    version_assets = rctx.attr.version_assets

    build_content = [
        'package(default_visibility = ["//visibility:public"])',
        "",
    ]

    for object_hash in asset_objects.keys():
        object_path = asset_objects[object_hash]
        rctx.download(
            url = "https://resources.download.minecraft.net/%s" % object_path,
            output = "objects/%s" % object_path,
            integrity = hex_sha1_to_sri(object_hash),
        )

    manifests = {}
    for manifest_id in asset_manifests.keys():
        manifest_text = asset_manifests[manifest_id]
        rctx.file(
            "indexes/%s.json" % manifest_id,
            content = manifest_text,
        )
        manifest = json.decode(manifest_text)
        manifests[manifest_id] = manifest
        build_content.append("filegroup(")
        build_content.append('    name = "objects_%s",' % manifest_id)
        build_content.append("    srcs = [")
        for asset_item in manifest["objects"].values():
            asset_hash = asset_item["hash"]
            asset_path = asset_objects[asset_hash]
            build_content.append('        "objects/%s",' % asset_path)
        build_content.append("    ],")
        build_content.append(")")

    for version_id in version_assets.keys():
        version_manifest = version_assets[version_id]
        rctx.file(
            "versions/%s" % version_id,
            content = version_manifest,
        )
        build_content.append("alias(")
        build_content.append('    name = "indexes_%s",' % version_id)
        build_content.append('    actual = "indexes/%s.json",' % version_manifest)
        build_content.append(")")
        build_content.append("alias(")
        build_content.append('    name = "objects_%s",' % version_id)
        build_content.append('    actual = ":objects_%s",' % version_manifest)
        build_content.append(")")
        build_content.append("filegroup(")
        build_content.append('    name = "assets_%s",' % version_id)
        build_content.append("    srcs = [")
        build_content.append('        ":indexes_%s",' % version_id)
        build_content.append('        ":objects_%s",' % version_id)
        build_content.append('        "versions/%s",' % version_id)
        build_content.append("    ],")
        build_content.append(")")

    build_content.append("alias(")
    build_content.append('    name = "assets",')
    build_content.append('    actual = ".",')
    build_content.append(")")

    rctx.file(
        "BUILD.bazel",
        content = "\n".join(build_content),
    )

    return None

minecraft_assets_repo = repository_rule(
    implementation = _minecraft_assets_repo_impl,
    attrs = {
        "asset_objects": attr.string_dict(),
        "asset_manifests": attr.string_dict(),
        "version_assets": attr.string_dict(),
    }
)

def _minecraft_impl(mctx):
    manifest_url = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
    manifest_path = "version_manifest.json"

    mctx.report_progress("Downloading version manifest")
    mctx.download(
        url = manifest_url,
        output = manifest_path,
    )
    manifest = json.decode(mctx.read(manifest_path))

    # Deduplicate version entries
    version_entries = {}
    for mod in mctx.modules:
        for minecraft_jar in mod.tags.minecraft_jar:
            key = (minecraft_jar.version, minecraft_jar.type)
            if key in version_entries:
                version_entries[key]["mapping"] |= minecraft_jar.mapping
                version_entries[key]["assets"] |= minecraft_jar.assets
            else:
                version_entries[key] = {
                    "version": minecraft_jar.version,
                    "type": minecraft_jar.type,
                    "mapping": minecraft_jar.mapping,
                    "assets": minecraft_jar.assets,
                }
    version_entries = version_entries.values()

    version_repo_names = []
    version_libraries = {}
    library_entries = {}
    asset_entries = {}
    version_assets = {}
    exclude_library_names = []
    for mod in mctx.modules:
        for exclude_library in mod.tags.exclude_library:
            for name in exclude_library.names:
                exclude_library_names.append(name)

    def escape_library_name(name):
        return name.replace(".", "_").replace(":", "_")

    def get_library_repo_name(name):
        return "minecraft_%s" % escape_library_name(name)

    for version_entry in version_entries:
        target_version = version_entry["version"]
        target_type = version_entry["type"]
        target_mapping = version_entry["mapping"]
        target_assets = version_entry["assets"]

        # Find version metadata
        version_entry = None
        for entry in manifest["versions"]:
            if entry["id"] == target_version:
                version_entry = entry
                break
        if not version_entry:
            fail("Version %s not found in manifest" % target_version)

        # Download version JSON
        version_json_path = "version_{}.json".format(target_version)
        mctx.report_progress("Downloading %s manifest" % target_version)
        mctx.download(
            url = version_entry["url"],
            output = version_json_path,
            integrity = hex_sha1_to_sri(version_entry["sha1"]),
        )
        version_data = json.decode(mctx.read(version_json_path))

        # Extract JAR info
        jar_info = version_data["downloads"].get(target_type)
        if not jar_info:
            fail("Type '%s' not found in version %s's data" % (target_type, target_version))

        # Create repository for JAR
        repo_name = "%s_%s" % (target_version, target_type)
        http_file(
            name = "minecraft_%s" % repo_name,
            url = jar_info["url"],
            integrity = hex_sha1_to_sri(jar_info["sha1"]),
            downloaded_file_path = "%s.jar" % target_type,
        )
        version_repo_names.append(repo_name)

        # Create repository for mapping
        if target_mapping:
            mapping_info = version_data["downloads"].get("%s_mappings" % target_type)
            if mapping_info == None:
                fail("No mappings for version %s" % target_version)

            # Create mapping repository
            mapping_repo_name = "%s_%s_mapping" % (target_version, target_type)
            http_file(
                name = "minecraft_%s" % mapping_repo_name,
                url = mapping_info["url"],
                integrity = hex_sha1_to_sri(mapping_info["sha1"]),
                downloaded_file_path = "mappings.txt",
            )
            version_repo_names.append(mapping_repo_name)

        if target_assets:
            asset_info = version_data["assetIndex"]
            if asset_info == None:
                fail("No assets for version %s" % target_version)
            asset_id = asset_info["id"]
            version_assets[target_version] = asset_id
            if asset_id not in asset_entries:
                asset_entries[asset_id] = {
                    "sha1": asset_info["sha1"],
                    "url": asset_info["url"],
                }

        # Append library entries
        libraries = []
        for library in version_data["libraries"]:
            name = library["name"]
            if name in exclude_library_names:
                continue
            library_repo_name = get_library_repo_name(name)
            libraries.append('"@%s//file"' % library_repo_name)

            if library_entries.get(name):
                continue
            escaped_name = escape_library_name(name)
            downloads = library["downloads"]["artifact"]
            library_entries[name] = {
                "name": escaped_name,
                "sha1": downloads["sha1"],
                "url": downloads["url"],
                "path": downloads["path"],
            }

        version_libraries[repo_name] = ",\n        ".join(libraries)

    # Download asset manifests
    asset_objects = {}
    asset_manifests = {}
    for asset_id in asset_entries.keys():
        asset_entry = asset_entries[asset_id]
        asset_manifest_path = "indexes/version_{}.json".format(asset_id)
        mctx.report_progress("Downloading asset %s manifest" % asset_id)
        mctx.download(
            url = asset_entry["url"],
            output = asset_manifest_path,
            integrity = hex_sha1_to_sri(asset_entry["sha1"]),
        )
        asset_manifest_text = mctx.read(asset_manifest_path)
        asset_manifests[asset_id] = asset_manifest_text
        asset_manifest = json.decode(asset_manifest_text)
        for asset_item in asset_manifest["objects"].values():
            asset_hash = asset_item["hash"]
            asset_path = "{}/{}".format(asset_hash[0:2], asset_hash)
            asset_objects[asset_hash] = asset_path

    # Create repositories for libraries
    for library_entry_name in library_entries.keys():
        library_entry = library_entries[library_entry_name]
        repo_name = get_library_repo_name(library_entry["name"])
        http_file(
            name = repo_name,
            url = library_entry["url"],
            integrity = hex_sha1_to_sri(library_entry["sha1"]),
            downloaded_file_path = library_entry["path"],
        )

    minecraft_repo(
        name = "minecraft",
        version_repo_names = version_repo_names,
        version_libraries = version_libraries,
    )

    minecraft_assets_repo(
        name = "minecraft_assets",
        asset_objects = asset_objects,
        asset_manifests = asset_manifests,
        version_assets = version_assets,
    )

minecraft = module_extension(
    implementation = _minecraft_impl,
    tag_classes = {
        "minecraft_jar": minecraft_jar,
        "exclude_library": exclude_library,
    },
)
