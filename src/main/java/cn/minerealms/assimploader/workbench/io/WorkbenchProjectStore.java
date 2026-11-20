package cn.minerealms.assimploader.workbench.io;

import cn.minerealms.assimploader.core.AssimpModelLoader;
import cn.minerealms.assimploader.core.ModelImportMode;
import cn.minerealms.assimploader.model.AssimpScene;
import cn.minerealms.assimploader.workbench.model.SceneMetadata;
import cn.minerealms.assimploader.workbench.model.WorkbenchDocument;
import cn.minerealms.assimploader.resource.LocalModelResources;
import com.google.gson.JsonElement;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Optional;

/** Blocking file service. Call import/open/export on a bounded worker, never from render(). */
public final class WorkbenchProjectStore {
    public static final String PROJECT_FILE="project.workbench.json";
    private static final String DRAFT_FILE="draft.workbench.json";
    private static final Gson DRAFT_JSON=new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Set<String> MODEL_EXTENSIONS=Set.of("fbx","gltf","glb","obj","dae","ply","stl","blend","3ds");
    private static final long MAX_BUNDLE_BYTES=2L*1024*1024*1024;
    private final Path root;

    public record Project(Path directory, Path documentPath, AssimpScene scene,
                          WorkbenchDocument document, SceneMetadata metadata) {}
    public record Recovery(Optional<WorkbenchDocument> document, List<String> issues) {
        public Recovery {document=java.util.Objects.requireNonNull(document);issues=List.copyOf(issues);}
    }

    public WorkbenchProjectStore(Path projectsRoot) {
        root=projectsRoot.toAbsolutePath().normalize();
        if(root.getParent()==null)throw new IllegalArgumentException("Project directory must not be a filesystem root");
    }
    public Path root(){return root;}
    /** Removes only the newly created project passed back by an unsuccessful UI import transaction. */
    public void discardImported(Project project)throws IOException {
        Path directory=project.directory().toAbsolutePath().normalize();
        if(!root.equals(directory.getParent())||!project.documentPath().toAbsolutePath().normalize().equals(directory.resolve(PROJECT_FILE))
                ||Files.isSymbolicLink(directory))throw new IOException("Canceled import is outside the project directory");
        removeOwnedImport(directory);
    }
    public static List<String> modelExtensions(){return MODEL_EXTENSIONS.stream().sorted().toList();}

    public List<Path> listProjects() throws IOException {
        if(!Files.isDirectory(root))return List.of();
        try(var directories=Files.list(root)) {
            return directories.filter(path->Files.isDirectory(path,LinkOption.NOFOLLOW_LINKS))
                    .map(path->path.resolve(PROJECT_FILE)).filter(Files::isRegularFile).sorted().toList();
        }
    }

    public Project importAsset(Path input) throws IOException {
        return importAsset(input, null);
    }

    /** Copies an explicit bounded bundle root while preserving model-relative resource paths. */
    public Project importAsset(Path input, Path bundleRoot) throws IOException {
        Path requested=input.toAbsolutePath().normalize();
        if(Files.isRegularFile(requested)&&extension(requested).equals("zip"))return importZip(requested);
        if(Files.isRegularFile(requested)&&requested.getFileName().toString().endsWith(".workbench.json"))return open(requested);
        if(Files.isDirectory(requested)&&Files.isRegularFile(requested.resolve(PROJECT_FILE)))return open(requested);
        Path model=selectModel(requested);
        Path bundle=bundleRoot==null?null:bundleRoot.toAbsolutePath().normalize();
        if(bundle==null){var resources=LocalModelResources.open(model);if(!resources.root().equals(model.getParent()))bundle=resources.root();}
        if(bundle!=null&&(!model.startsWith(bundle)||!model.toRealPath().startsWith(bundle.toRealPath())))
            throw new IOException("Model escapes portable import bundle");
        AssimpScene sourceScene=new AssimpModelLoader().loadModel(model,ModelImportMode.DYNAMIC);
        Files.createDirectories(root);
        Path staging=Files.createTempDirectory(root,".import-");
        try {
            Path assets=staging.resolve("assets");Files.createDirectories(assets);
            Path modelRelative=bundle==null?Path.of(model.getFileName().toString()):bundle.relativize(model);
            Path copiedModel=assets.resolve(modelRelative);
            var files=new LinkedHashMap<Path,Path>();files.put(model,copiedModel);
            var sourceToPortable=new LinkedHashMap<String,String>();
            var extraIssues=new ArrayList<String>();
            for(var material:sourceScene.getMaterials())for(var reference:WorkbenchDocument.MaterialOverride.from(sourceScene,material).textures().values()) {
                if(sourceScene.findEmbeddedTextureIndex(reference)>=0)continue;
                Path dependency=SceneMetadata.resolveDependency(model,reference);
                if(dependency==null||!Files.isRegularFile(dependency))continue;
                Path relative=bundle!=null&&dependency.startsWith(bundle)?bundle.relativize(dependency):portableDependency(model,dependency);
                Path destination=assets.resolve(relative).normalize();
                if(!destination.startsWith(assets))throw new IOException("Dependency escapes imported project");
                Path collision=files.entrySet().stream().filter(entry->entry.getValue().equals(destination))
                        .map(Map.Entry::getKey).findFirst().orElse(null);
                if(collision!=null&&!collision.equals(dependency))throw new IOException("Ambiguous dependency path: "+relative);
                files.put(dependency,destination);sourceToPortable.put(reference,slash(copiedModel.getParent().relativize(destination)));
                if(!safeRelative(reference))extraIssues.add("Texture reference remapped in project: "+reference);
            }
            collectContainerDependencies(model,copiedModel.getParent(),files,extraIssues);
            if(bundle==null)collectAdjacentDependencies(model,copiedModel.getParent(),files,extraIssues);
            if(bundle!=null)collectBundleDependencies(bundle,assets,files);
            else if(Files.isDirectory(requested))collectFolderDependencies(requested,model,assets,files);
            long bytes=0;
            for(var entry:files.entrySet()) {
                if(Thread.currentThread().isInterrupted())throw new IOException("Import cancelled");
                bytes=Math.addExact(bytes,Files.size(entry.getKey()));
                if(bytes>MAX_BUNDLE_BYTES)throw new IOException("Import bundle exceeds 2 GiB");
                Files.createDirectories(entry.getValue().getParent());
                Files.copy(entry.getKey(),entry.getValue(),StandardCopyOption.REPLACE_EXISTING);
            }
            String hash=hashFiles(assets);
            String stem=stem(model.getFileName().toString()).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]","_");
            if(stem.isBlank())stem="model";
            if(stem.length()>64)stem=stem.substring(0,64);
            String id="workbench:"+stem+"_"+hash.substring(0,12);
            WorkbenchDocument document=WorkbenchDocument.defaults(sourceScene,id,"assets/"+slash(modelRelative),hash);
            for(var entry:document.materials().entrySet()) {
                var material=entry.getValue();var textures=new LinkedHashMap<>(material.textures());
                textures.replaceAll((slot,reference)->sourceToPortable.getOrDefault(reference,reference));
                document=document.withMaterial(entry.getKey(),new WorkbenchDocument.MaterialOverride(material.r(),material.g(),material.b(),material.a(),
                        material.metallic(),material.roughness(),material.emission(),material.alphaMode(),material.cutoff(),material.doubleSided(),textures));
            }
            writeAtomic(staging.resolve(PROJECT_FILE),WorkbenchCodec.write(document));
            Path published=root.resolve(stem+"-"+hash.substring(0,12)+"-"+UUID.randomUUID().toString().substring(0,8));
            move(staging,published);
            AssimpScene scene=new AssimpModelLoader().loadModel(published.resolve(document.sourcePath()),ModelImportMode.DYNAMIC);
            SceneMetadata metadata=SceneMetadata.inspect(scene,document);
            if(!extraIssues.isEmpty()) {
                var issues=new ArrayList<>(metadata.issues());issues.addAll(extraIssues);
                metadata=new SceneMetadata(metadata.nodes(),metadata.meshes(),metadata.materials(),metadata.clips(),metadata.bounds(),issues,
                        metadata.skinned(),metadata.embeddedTextures(),metadata.sourceFormat(),metadata.sourceGenerator());
            }
            return new Project(published,published.resolve(PROJECT_FILE),scene,document,metadata);
        } finally {
            if(Files.isDirectory(staging,LinkOption.NOFOLLOW_LINKS))removeStaging(staging);
        }
    }

    public Project open(Path projectFile) throws IOException {
        Path file=projectFile.toAbsolutePath().normalize();
        if(Files.isRegularFile(file)&&extension(file).equals("zip"))return importZip(file);
        if(Files.isDirectory(file))file=file.resolve(PROJECT_FILE);
        WorkbenchDocument document=read(file);
        Path directory=file.getParent();
        Path model=resolvePortable(directory,document.sourcePath());
        AssimpScene scene=new AssimpModelLoader().loadModel(model,ModelImportMode.DYNAMIC);
        SceneMetadata metadata=SceneMetadata.inspect(scene,document);
        var referenceIssues=document.validateReferences(metadata);
        if(!referenceIssues.isEmpty()) {
            var issues=new ArrayList<>(metadata.issues());issues.addAll(referenceIssues);
            metadata=new SceneMetadata(metadata.nodes(),metadata.meshes(),metadata.materials(),metadata.clips(),metadata.bounds(),issues,
                    metadata.skinned(),metadata.embeddedTextures(),metadata.sourceFormat(),metadata.sourceGenerator());
        }
        Path assets=directory.resolve("assets");
        if(!document.contentHash().isEmpty()&&Files.isDirectory(assets)&&!document.contentHash().equals(hashFiles(assets))) {
            var issues=new ArrayList<>(metadata.issues());issues.add("Asset content changed since import; review before sharing or re-saving");
            metadata=new SceneMetadata(metadata.nodes(),metadata.meshes(),metadata.materials(),metadata.clips(),metadata.bounds(),issues,
                    metadata.skinned(),metadata.embeddedTextures(),metadata.sourceFormat(),metadata.sourceGenerator());
        }
        return new Project(directory,file,scene,document,metadata);
    }

    public WorkbenchDocument read(Path file) throws IOException {
        if(Files.size(file)>WorkbenchCodec.MAX_BYTES)throw new IOException("Project JSON exceeds 4 MiB");
        try{return WorkbenchCodec.read(Files.readString(file,StandardCharsets.UTF_8));}
        catch(RuntimeException invalid){throw new IOException("Invalid workbench project: "+invalid.getMessage(),invalid);}
    }
    public void save(Path file,WorkbenchDocument document) throws IOException {
        writeAtomic(checkedSavePath(file,document),WorkbenchCodec.write(document));
    }
    private Path checkedSavePath(Path file,WorkbenchDocument document) throws IOException {
        Path absolute=file.toAbsolutePath().normalize();
        if(!absolute.startsWith(root))throw new IOException("Project save must stay inside configured project directory");
        Files.createDirectories(absolute.getParent());
        if(!absolute.getParent().toRealPath().startsWith(root.toRealPath()))throw new IOException("Project save escapes through symbolic link");
        resolvePortable(absolute.getParent(),document.sourcePath());
        return absolute;
    }

    public void saveDraft(Path projectFile,WorkbenchDocument document) throws IOException {
        saveDraft(projectFile,read(projectFile),document);
    }
    /** The base comes from the edit session, so external edits cannot silently become this draft's base. */
    public void saveDraft(Path projectFile,WorkbenchDocument savedBase,WorkbenchDocument document) throws IOException {
        if(!sameAsset(savedBase,document))throw new IOException("Recovery draft belongs to another asset");
        Path draft=checkedSavePath(projectFile.resolveSibling(DRAFT_FILE),document);
        JsonObject json=WorkbenchCodec.toJson(document),recovery=new JsonObject();
        recovery.addProperty("version",1);recovery.addProperty("base_sha256",documentHash(savedBase));json.add("_recovery",recovery);
        String encoded=DRAFT_JSON.toJson(json)+"\n";
        if(encoded.getBytes(StandardCharsets.UTF_8).length>WorkbenchCodec.MAX_BYTES)throw new IOException("Recovery JSON exceeds 4 MiB");
        writeAtomic(draft,encoded);
    }
    public Optional<WorkbenchDocument> readDraft(Path projectFile) throws IOException {
        if(!Files.exists(projectFile.resolveSibling(DRAFT_FILE),LinkOption.NOFOLLOW_LINKS))return Optional.empty();
        return readRecovery(projectFile,read(projectFile)).document();
    }
    /** A bad optional recovery file must never prevent opening its valid formal project. */
    public Recovery readRecovery(Path projectFile,WorkbenchDocument saved) {
        Path draft=projectFile.resolveSibling(DRAFT_FILE);
        if(!Files.exists(draft,LinkOption.NOFOLLOW_LINKS))return new Recovery(Optional.empty(),List.of());
        try {
            if(!Files.isRegularFile(draft,LinkOption.NOFOLLOW_LINKS))throw new IOException("Recovery entry is not a regular file");
            if(Files.size(draft)>WorkbenchCodec.MAX_BYTES)throw new IOException("Recovery JSON exceeds 4 MiB");
            var json=JsonParser.parseString(Files.readString(draft,StandardCharsets.UTF_8)).getAsJsonObject();
            if(!json.has("_recovery"))return ignoredDraft("Legacy recovery draft has no saved-version reference; saved project kept");
            var reference=json.getAsJsonObject("_recovery");
            if(reference.get("version").getAsInt()!=1)throw new IOException("Unsupported recovery metadata version");
            if(!documentHash(saved).equals(reference.get("base_sha256").getAsString()))
                return ignoredDraft("Stale recovery draft references another saved version; saved project kept");
            if(Files.getLastModifiedTime(projectFile).compareTo(Files.getLastModifiedTime(draft))>0)
                return ignoredDraft("Recovery draft is older than the saved project; saved project kept");
            WorkbenchDocument document=WorkbenchCodec.fromJson(json);
            if(!sameAsset(saved,document))return ignoredDraft("Recovery draft belongs to another asset; saved project kept");
            return new Recovery(document.equals(saved)?Optional.empty():Optional.of(document),List.of());
        } catch(IOException|RuntimeException invalid) {
            return ignoredDraft("Invalid recovery draft ignored; saved project kept: "+invalid.getMessage());
        }
    }
    private static Recovery ignoredDraft(String issue) {return new Recovery(Optional.empty(),List.of(issue));}
    private static boolean sameAsset(WorkbenchDocument a,WorkbenchDocument b) {
        return a.assetId().equals(b.assetId())&&a.sourcePath().equals(b.sourcePath())&&a.contentHash().equals(b.contentHash());
    }
    private static String documentHash(WorkbenchDocument document) {
        try {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(WorkbenchCodec.write(document).getBytes(StandardCharsets.UTF_8)));}
        catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
    public void discardDraft(Path projectFile) throws IOException {
        Path draft=projectFile.toAbsolutePath().normalize().resolveSibling(DRAFT_FILE);
        if(!draft.startsWith(root))throw new IOException("Draft must stay inside configured project directory");
        if(Files.exists(draft)&&!draft.toRealPath().startsWith(root.toRealPath()))throw new IOException("Draft escapes through symbolic link");
        Files.deleteIfExists(draft);
    }

    /** Export the accepted document and its local bundle. The source project is not modified. */
    public Path exportZip(Project project,WorkbenchDocument document,Path destination) throws IOException {
        return exportZip(project,document,destination,Map.of());
    }
    /**
     * Additional consumer files use portable paths under extensions/ and never execute during import.
     * An empty entry named extensions/modid/folder/ replaces that complete subtree in the archive;
     * the marker itself is omitted. With no marker, existing files retain the legacy merge behavior.
     */
    public Path exportZip(Project project,WorkbenchDocument document,Path destination,Map<String,byte[]> extensionFiles) throws IOException {
        Path output=destination.toAbsolutePath().normalize();
        Files.createDirectories(output.getParent());
        Path temp=Files.createTempFile(output.getParent(),".workbench-export-",".zip");
        try {
            WorkbenchPackageIO.write(project,document,temp,extensionFiles);
            move(temp,output);return output;
        } finally {Files.deleteIfExists(temp);}
    }

    /** Extract into a new isolated project; a failed package never overwrites an existing project. */
    public Project importZip(Path archive) throws IOException {
        Files.createDirectories(root);
        Path staging=Files.createTempDirectory(root,".import-");
        Path published=null;
        boolean complete=false;
        try {
            var extracted=WorkbenchPackageIO.extract(archive.toAbsolutePath().normalize(),staging);
            var document=extracted.document();
            if(!MODEL_EXTENSIONS.contains(extension(Path.of(document.sourcePath()))))throw new IOException("Package source is not a supported model");
            String name=stem(Path.of(document.sourcePath()).getFileName().toString()).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]","_");
            if(name.isBlank())name="model";
            if(name.length()>64)name=name.substring(0,64);
            Path destination=root.resolve(name+"-package-"+UUID.randomUUID());
            if(Files.exists(destination,LinkOption.NOFOLLOW_LINKS))throw new IOException("Package destination already exists");
            try{Files.move(staging,destination,StandardCopyOption.ATOMIC_MOVE);}
            catch(AtomicMoveNotSupportedException unsupported){Files.move(staging,destination);}
            published=destination;
            Project project=open(published);
            if(extracted.legacy()) {
                var metadata=project.metadata();var issues=new ArrayList<>(metadata.issues());
                issues.add("Legacy ZIP imported without manifest checksums; save and export to upgrade it");
                project=new Project(project.directory(),project.documentPath(),project.scene(),project.document(),
                        new SceneMetadata(metadata.nodes(),metadata.meshes(),metadata.materials(),metadata.clips(),metadata.bounds(),issues,
                                metadata.skinned(),metadata.embeddedTextures(),metadata.sourceFormat(),metadata.sourceGenerator()));
            }
            complete=true;return project;
        } finally {
            if(Files.isDirectory(staging,LinkOption.NOFOLLOW_LINKS))removeStaging(staging);
            if(!complete&&published!=null&&Files.isDirectory(published,LinkOption.NOFOLLOW_LINKS))removeOwnedImport(published);
        }
    }

    public static Path resolvePortable(Path directory,String reference) throws IOException {
        if(!safeRelative(reference))throw new IOException("Project source must be a relative path inside its directory");
        Path root=directory.toAbsolutePath().normalize();Path path=root.resolve(reference.replace('\\','/')).normalize();
        if(!path.startsWith(root))throw new IOException("Project path escapes its directory");
        if(Files.exists(path)&&!path.toRealPath().startsWith(root.toRealPath()))throw new IOException("Project path escapes through symbolic link");
        return path;
    }
    public static String hashFiles(Path directory) throws IOException {
        try {
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            try(var paths=Files.walk(directory)) {
                for(Path path:paths.filter(file->Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)).sorted(Comparator.comparing(path->slash(directory.relativize(path)))).toList()) {
                    digest.update(slash(directory.relativize(path)).getBytes(StandardCharsets.UTF_8));digest.update((byte)0);
                    try(InputStream input=Files.newInputStream(path)) {byte[] buffer=new byte[65536];for(int count;(count=input.read(buffer))>=0;)digest.update(buffer,0,count);}
                    digest.update((byte)0);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
    private static Path selectModel(Path input) throws IOException {
        if(Files.isRegularFile(input)&&MODEL_EXTENSIONS.contains(extension(input)))return input;
        if(!Files.isDirectory(input))throw new IOException("Choose a model file or a folder containing one model");
        try(var paths=Files.walk(input,8)) {
            List<Path> models=paths.filter(path->Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)&&MODEL_EXTENSIONS.contains(extension(path))).sorted().toList();
            if(models.size()==1)return models.get(0);
            if(models.isEmpty())throw new IOException("No supported model found in folder");
            List<Path> top=models.stream().filter(path->path.getParent().equals(input)).toList();
            if(top.size()==1)return top.get(0);
            throw new IOException("Folder has multiple models; select one model file explicitly");
        }
    }
    private static Path portableDependency(Path model,Path dependency) {
        Path parent=model.toAbsolutePath().normalize().getParent();Path absolute=dependency.toAbsolutePath().normalize();
        return absolute.startsWith(parent)?parent.relativize(absolute):Path.of("textures",dependency.getFileName().toString());
    }
    private static void collectFolderDependencies(Path folder,Path model,Path assets,Map<Path,Path> files) throws IOException {
        Path parent=model.getParent();
        Set<String> extra=Set.of("png","jpg","jpeg","tga","dds","bmp","webp","bin","mtl","json","ktx2");
        try(var paths=Files.walk(parent,8)) {
            List<Path> entries=paths.filter(path->Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)&&extra.contains(extension(path))).limit(10001).toList();
            if(entries.size()>10000)throw new IOException("Import bundle exceeds 10000 dependency files");
            for(Path file:entries)if(file.startsWith(folder))files.putIfAbsent(file,assets.resolve(parent.relativize(file)));
        }
    }

    private static void collectBundleDependencies(Path bundle,Path assets,Map<Path,Path> files)throws IOException {
        try(var paths=Files.walk(bundle,32)){
            var iterator=paths.iterator();
            while(iterator.hasNext()){
                Path file=iterator.next();
                if(Files.isSymbolicLink(file))throw new IOException("Portable import bundle contains a symbolic link");
                if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS))continue;
                if(files.size()>=10000&&!files.containsKey(file))throw new IOException("Import bundle exceeds 10000 dependency files");
                if(!file.toRealPath().startsWith(bundle.toRealPath()))throw new IOException("Portable import dependency escapes its bundle");
                files.putIfAbsent(file,assets.resolve(bundle.relativize(file)));
            }
        }
    }

    /** Copy model sidecars and adjacent definitions that explicitly identify this model. */
    private static void collectAdjacentDependencies(Path model,Path assets,Map<Path,Path> files,List<String> issues)throws IOException {
        var resources=LocalModelResources.open(model);
        var queued=new java.util.ArrayDeque<Path>();var visited=new java.util.HashSet<Path>();
        try(var siblings=Files.list(model.getParent())){
            for(Path file:siblings.filter(path->path.getFileName().toString().endsWith(".json")).sorted().toList()){
                if(Files.isSymbolicLink(file))throw new IOException("Adjacent model JSON cannot be a symbolic link");
                if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS))continue;
                if(file.equals(LocalModelResources.instancePath(model))||file.equals(LocalModelResources.indexPath(model))){queued.add(file);continue;}
                try{
                    var json=resources.json(file);
                    if(json.has("model")&&json.get("model").isJsonPrimitive()){
                        String declared=json.get("model").getAsString();
                        String filename=declared.substring(Math.max(declared.lastIndexOf('/'),declared.lastIndexOf('\\'))+1);
                        if(filename.equals(model.getFileName().toString()))queued.add(file);
                    }
                }catch(IOException|RuntimeException unrelated){/* Unassociated files are not part of an individual model import. */}
            }
        }
        var sidecar=LocalModelResources.instancePath(model);
        if(Files.isRegularFile(sidecar,LinkOption.NOFOLLOW_LINKS)){
            var json=resources.json(sidecar);
            if(json.has("model")&&json.get("model").isJsonPrimitive()&&json.get("model").getAsString().contains(":"))
                resources.originalModel(cn.minerealms.assimploader.util.ResourceLocations.parse(json.get("model").getAsString()));
        }
        if(resources.originalModel().isEmpty())for(Path candidate:queued){
            try{
                var json=resources.json(candidate);
                if(!json.has("model")||!json.get("model").isJsonPrimitive())continue;
                String reference=json.get("model").getAsString();if(!reference.contains(":"))continue;
                var identity=cn.minerealms.assimploader.util.ResourceLocations.parse(reference);
                if(Path.of(identity.getPath()).getFileName().equals(model.getFileName())){resources.originalModel(identity);break;}
            }catch(RuntimeException|IOException invalid){issues.add("Cannot inspect adjacent model JSON: "+candidate.getFileName());}
        }
        while(!queued.isEmpty()){
            Path file=queued.remove().toAbsolutePath().normalize();if(!visited.add(file))continue;
            if(visited.size()>512)throw new IOException("Model JSON dependency graph exceeds 512 files");
            Path relative=model.getParent().relativize(file);
            Path destination=assets.resolve(relative).normalize();
            if(relative.startsWith("..")){issues.add("Adjacent JSON dependency needs bundle import: "+relative);continue;}
            files.putIfAbsent(file,destination);
            JsonElement json;
            try{json=resources.json(file);}catch(IOException invalid){issues.add("Cannot read adjacent JSON "+file.getFileName()+": "+invalid.getMessage());continue;}
            for(String reference:references(json)){
                if(!resourceFile(reference))continue;
                try{
                    Path dependency=resources.resolve(reference,file);
                    if(!Files.isRegularFile(dependency,LinkOption.NOFOLLOW_LINKS)){issues.add("Missing JSON dependency: "+reference);continue;}
                    Path local=model.getParent().relativize(dependency);
                    if(local.startsWith("..")){issues.add("JSON dependency needs bundle import: "+reference);continue;}
                    files.putIfAbsent(dependency,assets.resolve(local));
                    if(reference.endsWith(".json"))queued.add(dependency);
                }catch(IOException|RuntimeException invalid){issues.add("Unavailable JSON dependency "+reference+": "+invalid.getMessage());}
            }
        }
    }

    private static List<String> references(JsonElement value){
        var result=new ArrayList<String>();collectReferences(value,result,0);return result;
    }
    private static void collectReferences(JsonElement value,List<String> result,int depth){
        if(depth>32||result.size()>4096)throw new IllegalArgumentException("Model JSON dependency graph is too complex");
        if(value.isJsonObject())value.getAsJsonObject().entrySet().forEach(e->collectReferences(e.getValue(),result,depth+1));
        else if(value.isJsonArray())value.getAsJsonArray().forEach(e->collectReferences(e,result,depth+1));
        else if(value.isJsonPrimitive()&&value.getAsJsonPrimitive().isString())result.add(value.getAsString());
    }
    private static boolean resourceFile(String value){return value.matches("(?i).+\\.(json|fbx|gltf|glb|obj|dae|png|jpg|jpeg|tga|dds|bin|mtl|ktx2)");}
    private static void collectContainerDependencies(Path model,Path assets,Map<Path,Path> files,List<String> issues) throws IOException {
        if(extension(model).equals("gltf")) {
            var json=JsonParser.parseString(Files.readString(model)).getAsJsonObject();
            for(String key:List.of("buffers","images"))if(json.has(key))for(var item:json.getAsJsonArray(key)) {
                var object=item.getAsJsonObject();if(object.has("uri"))dependency(model,object.get("uri").getAsString(),assets,files,issues);
            }
        } else if(extension(model).equals("obj")) {
            try(var lines=Files.lines(model)) {
                for(String line:lines.filter(value->value.stripLeading().startsWith("mtllib ")).toList())
                    dependency(model,line.strip().substring(7).strip(),assets,files,issues);
            }
        }
    }
    private static void dependency(Path model,String reference,Path assets,Map<Path,Path> files,List<String> issues) {
        if(reference.startsWith("data:"))return;
        Path source=SceneMetadata.resolveDependency(model,reference);
        if(source==null||!Files.isRegularFile(source)){issues.add("Missing container dependency: "+reference);return;}
        if(!safeRelative(reference)){issues.add("Container dependency needs a relative source path: "+reference);return;}
        files.put(source,assets.resolve(reference.replace('\\','/')).normalize());
    }
    private static boolean safeRelative(String path) {
        if(path==null||path.isBlank()||path.indexOf('\0')>=0)return false;
        String value=path.replace('\\','/');if(value.startsWith("/")||value.contains(":"))return false;
        try {Path parsed=Path.of(value).normalize();return !parsed.isAbsolute()&&!parsed.startsWith("..");}
        catch(RuntimeException invalid){return false;}
    }
    private static void writeAtomic(Path destination,String json) throws IOException {
        Path temp=Files.createTempFile(destination.getParent(),".project-",".tmp");
        try{Files.writeString(temp,json,StandardCharsets.UTF_8);move(temp,destination);}finally{Files.deleteIfExists(temp);}
    }
    private static void move(Path source,Path target) throws IOException {
        try{Files.move(source,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
        catch(AtomicMoveNotSupportedException unsupported){Files.move(source,target,StandardCopyOption.REPLACE_EXISTING);}
    }
    private void removeStaging(Path staging) throws IOException {
        Path verified=staging.toAbsolutePath().normalize();
        if(!verified.getParent().equals(root)||!verified.getFileName().toString().startsWith(".import-"))throw new IOException("Invalid staging cleanup path");
        removeOwnedImport(verified);
    }
    private void removeOwnedImport(Path directory) throws IOException {
        Path verified=directory.toAbsolutePath().normalize();
        if(!verified.getParent().equals(root)||!Files.isDirectory(verified,LinkOption.NOFOLLOW_LINKS))throw new IOException("Invalid import cleanup path");
        try(var paths=Files.walk(verified)) {for(Path path:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(path);}
    }
    private static String extension(Path path){String name=path.getFileName().toString();int dot=name.lastIndexOf('.');return dot<0?"":name.substring(dot+1).toLowerCase(Locale.ROOT);}
    private static String stem(String name){int dot=name.lastIndexOf('.');return dot<0?name:name.substring(0,dot);}
    private static String slash(Path path){return path.toString().replace('\\','/');}
}
