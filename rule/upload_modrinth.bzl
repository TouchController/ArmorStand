"Rules for uploading mods to Modrinth"

def _modrinth_dependency_info_init(*, version_id, project_id, dependency_type):
    if not project_id:
        fail("project_id must be specified")
    allowed_dependency_types = [
        "required",
        "optional",
        "incompatible",
        "embedded",
    ]
    if dependency_type not in allowed_dependency_types:
        fail("dependency_type must be one of %s" % allowed_dependency_types)
    return {
        "version_id": version_id,
        "project_id": project_id,
        "dependency_type": dependency_type,
    }

ModrinthDependencyInfo, _ = provider(
    doc = "A modrith dependency",
    fields = [
        "version_id",
        "project_id",
        "dependency_type",
    ],
    init = _modrinth_dependency_info_init,
)

def _modrinth_dependency_impl(ctx):
    return [
        ModrinthDependencyInfo(
            version_id = ctx.attr.version_id,
            project_id = ctx.attr.project_id,
            dependency_type = ctx.attr.dependency_type,
        ),
    ]

modrinth_dependency = rule(
    implementation = _modrinth_dependency_impl,
    attrs = {
        "version_id": attr.string(
            doc = "The ID of the version to depend on.",
            mandatory = False,
        ),
        "project_id": attr.string(
            doc = "The ID of the project to depend on.",
            mandatory = True,
        ),
        "dependency_type": attr.string(
            doc = "The type of the dependency.",
            mandatory = True,
        ),
    },
)

def _upload_modrinth_impl(ctx):
    input_file = ctx.file.file
    changelog_file = ctx.file.changelog

    allowed_version_types = [
        "alpha",
        "beta",
        "release",
    ]
    if ctx.attr.version_type not in allowed_version_types:
        fail("version_type must be one of %s" % allowed_version_types)

    args = []
    args += ["--token-secret-id", ctx.attr.token_secret_id]
    args += ["--project-id", ctx.attr.project_id]
    args += ["--version-name", ctx.attr.version_name]
    args += ["--version-id", ctx.attr.version_id]
    args += ["--version-type", ctx.attr.version_type]
    for game_version in ctx.attr.game_versions:
        args += ["--game-version", game_version]
    for loader in ctx.attr.loaders:
        args += ["--loader", loader]
    for dependency_info in ctx.attr.deps:
        dependency = dependency_info[ModrinthDependencyInfo]
        args += ["--dependency", "--dependency-project-id", dependency.project_id]
        if dependency.version_id:
            args += ["--dependency-version-id", dependency.version_id]
        args += ["--dependency-type", dependency.dependency_type]
    args += ["--file-name", ctx.attr.file_name]

    runfiles = ctx.runfiles(
        files = [
            ctx.file._rlocation_library,
            input_file,
        ] + ([changelog_file] if changelog_file else []),
    ).merge(
        ctx.attr._modrinth_uploader_binary[DefaultInfo].default_runfiles,
    )

    ctx.actions.expand_template(
        output = ctx.outputs.executable,
        template = ctx.file._modrinth_uploader_wrapper,
        substitutions = {
            "{WORKSPACE_NAME}": ctx.workspace_name,
            "{CHANGELOG_PATH}": changelog_file.short_path if changelog_file else "",
            "{FILE_PATH}": input_file.short_path,
            "{EXEC_PATH}": ctx.executable._modrinth_uploader_binary.short_path,
            "{ARGS}": "\n".join([arg.replace("'", "\\'") for arg in args]),
        },
        is_executable = True,
    )

    return [DefaultInfo(
        runfiles = runfiles,
    )]

upload_modrinth = rule(
    implementation = _upload_modrinth_impl,
    executable = True,
    attrs = {
        "token_secret_id": attr.string(
            doc = "The secret ID of the token.",
            mandatory = True,
        ),
        "project_id": attr.string(
            doc = "The ID of the project to upload to.",
            mandatory = True,
        ),
        "version_name": attr.string(
            doc = "The name of the version to upload.",
            mandatory = True,
        ),
        "version_id": attr.string(
            doc = "The ID of the version to upload.",
            mandatory = True,
        ),
        "version_type": attr.string(
            doc = "The type of the version to upload. Can be one of alpha, beta, or release.",
            mandatory = True,
        ),
        "changelog": attr.label(
            doc = "The changelog file.",
            allow_single_file = [".md"],
            mandatory = False,
        ),
        "game_versions": attr.string_list(
            doc = "The game versions of the version to upload.",
            mandatory = True,
        ),
        "loaders": attr.string_list(
            doc = "The loaders of the version to upload.",
            mandatory = True,
        ),
        "deps": attr.label_list(
            doc = "The dependencies of the version to upload.",
            providers = [ModrinthDependencyInfo],
            mandatory = False,
        ),
        "file": attr.label(
            doc = "The file to upload.",
            mandatory = True,
            allow_single_file = True,
        ),
        "file_name": attr.string(
            doc = "The name of the file to upload.",
            mandatory = True,
        ),
        "_modrinth_uploader_binary": attr.label(
            default = "//rule/modrinth_uploader",
            executable = True,
            cfg = "exec",
        ),
        "_modrinth_uploader_wrapper": attr.label(
            default = "//rule/modrinth_uploader:modrinth_uploader_wrapper",
            allow_single_file = [".bash"],
        ),
        "_rlocation_library": attr.label(
            default = "@bazel_tools//tools/bash/runfiles",
            allow_single_file = [".bash"],
        ),
    },
)
