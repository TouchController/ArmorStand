def _extract_jar_impl(ctx):
    output_file = ctx.actions.declare_file(ctx.attr.filename)
    args = ctx.actions.args()

    args.add_all([
        ctx.file.input.path,
        ctx.attr.entry_path,
        output_file.path,
    ])

    ctx.actions.run(
        inputs = [ctx.file.input],
        outputs = [output_file],
        executable = ctx.executable._extract_bin,
        arguments = [args],
        progress_message = "Extracting %s" % ctx.label.name,
    )

    return [DefaultInfo(files = depset([output_file]))]

extract_jar = rule(
    implementation = _extract_jar_impl,
    attrs = {
        "input": attr.label(
            allow_single_file = [".jar", ".zip", ".aar"],
            mandatory = True,
            doc = "Input JAR file",
        ),
        "entry_path": attr.string(
            mandatory = True,
            doc = "Path of the entry to be extracted",
        ),
        "filename": attr.string(
            mandatory = True,
            doc = "Output file name",
        ),
        "_extract_bin": attr.label(
            default = Label("//rule/extract"),
            executable = True,
            cfg = "exec",
        ),
    },
    doc = "Extract single file from a JAR archive.",
)
