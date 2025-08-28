def _update_log_extractor_impl(ctx):
    output_file = ctx.actions.declare_file(ctx.label.name + ".md")
    input_files = depset(ctx.files.inputs)

    args = ctx.actions.args()
    args.add(ctx.attr.version)
    args.add(output_file.path)
    args.add_all(input_files.to_list())

    ctx.actions.run(
        executable = ctx.executable._extractor_tool,
        inputs = depset(
            direct = [ctx.executable._extractor_tool],
            transitive = [input_files],
        ),
        outputs = [output_file],
        arguments = [args],
        progress_message = "Extracting update log for version %s" % ctx.attr.version,
    )

    return [DefaultInfo(files = depset([output_file]))]

extract_update_log = rule(
    implementation = _update_log_extractor_impl,
    attrs = {
        "version": attr.string(
            mandatory = True,
            doc = "The version name to extract.",
        ),
        "inputs": attr.label_list(
            mandatory = True,
            allow_files = [".md"],
            doc = "Input Markdown files to extract logs from.",
        ),
        "_extractor_tool": attr.label(
            default = Label("//rule/update_log_extractor"),
            executable = True,
            cfg = "exec",
        ),
    },
    doc = "Extracts update logs from Markdown files based on a version name.",
)
