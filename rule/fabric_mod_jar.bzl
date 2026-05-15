def _fabric_mod_jar_impl(ctx):
    output = ctx.actions.declare_file(ctx.attr.output)
    src_files = []
    for src in ctx.attr.srcs:
        src_files.extend(src[DefaultInfo].files.to_list())
    jij_files = []
    for jij in ctx.attr.jars_in_jar:
        jij_files.extend(jij[DefaultInfo].files.to_list())
    pyscript = ctx.actions.declare_file("_fabric_pack/" + ctx.label.name + ".py")
    src_paths = [f.path for f in src_files if f.path.endswith(".jar")]
    jij_paths = [f.path for f in jij_files]
    jij_basenames = [f.basename for f in jij_files]
    py = """
import json,os,shutil,subprocess,sys,tempfile,zipfile
D=tempfile.mkdtemp()
os.makedirs(os.path.join(D,'META-INF','jars'),exist_ok=True)
for s in %r:
    with zipfile.ZipFile(s,'r') as z:
        z.extractall(D)
for j in %r:
    shutil.copy2(j,os.path.join(D,'META-INF','jars'))
fmj=os.path.join(D,'fabric.mod.json')
if os.path.exists(fmj):
    with open(fmj,'r') as f:
        d=json.load(f)
    d['version']='%s'
    d['jars']=[{'file':'META-INF/jars/'+b} for b in %r]
    with open(fmj,'w') as f:
        json.dump(d,f,indent=2,ensure_ascii=False)
out='%s'
with zipfile.ZipFile(out,'w',zipfile.ZIP_DEFLATED) as zout:
    for root,dirs,files in os.walk(D):
        for fn in files:
            fp=os.path.join(root,fn)
            zout.write(fp,os.path.relpath(fp,D))
shutil.rmtree(D)
""" % (src_paths, jij_paths, ctx.attr.version, jij_basenames, output.path)
    ctx.actions.write(output = pyscript, content = py, is_executable = True)
    ctx.actions.run(
        inputs = src_files + jij_files + [pyscript],
        outputs = [output],
        executable = "python3",
        arguments = [pyscript.path],
        progress_message = "Packaging Fabric mod JAR %s" % ctx.label.name,
        use_default_shell_env = True,
    )
    return [DefaultInfo(files = depset([output]))]
fabric_mod_jar = rule(
    implementation = _fabric_mod_jar_impl,
    attrs = {
        "srcs": attr.label_list(mandatory = True),
        "jars_in_jar": attr.label_list(default = []),
        "output": attr.string(mandatory = True),
        "version": attr.string(default = ""),
    },
)
