<div align="center">
<h1>Blaze3D Model</h1>
<p>
<img src="https://img.shields.io/badge/Bazel-9.1.0-43A047?logo=bazel&logoColor=white" alt="Bazel 9.1.0"/>
<img src="https://img.shields.io/badge/Minecraft-26.2--snapshot--7-4CAF50" alt="Minecraft"/>
<img src="https://img.shields.io/badge/Java-25-F57C00?logo=openjdk&logoColor=white" alt="Java 25"/>
<img src="https://img.shields.io/badge/Kotlin-2.3.21-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin"/>
<img src="https://img.shields.io/badge/Fabric-0.19.2-DB7093" alt="Fabric Loader"/>
<img src="https://img.shields.io/badge/License-CC0--1.0-blue" alt="License"/>
</p>
<p>A compact Fabric model pipeline mod for Minecraft 26.2 snapshot builds.</p>
<p>Loads Assimp-backed 3D formats, glTF, FBX, OBJ, DAE, YSM and MMD assets into a single runtime module, then connects animation, materials, texture upload, GPU batching, culling, physics, player replacement and network synchronization through Kotlin APIs.</p>
</div>

---

## Supported Formats

| Format | Extensions | Type |
| --- | --- | --- |
| glTF | `.gltf` `.glb` | Text / Binary |
| FBX | `.fbx` | Binary / Text |
| Wavefront OBJ | `.obj` | Text |
| Collada | `.dae` | Text |
| MikuMikuDance | `.pmx` `.pmd` `.vmd` `.vpd` | Binary |
| Yes Steve Model | `.ysm` | JSON |
| Assimp Universal | 60+ formats including `.3ds` `.blend` `.stl` `.vrm` `.x3d` `.ply` `.md2` `.md3` `.bvh` | Binary / Text |

## Features

**Model Pipeline** -- Single Fabric module loading pipeline with Assimp-backed universal import, async loading, hot reload, file fingerprint caching and model package resolution.

**Rendering** -- GPU batched rendering with compute skinning, indirect draw commands, frustum culling, BVH acceleration, automatic LOD generation, mesh vertex compression and multi-pass pipeline support.

**Advanced Rendering** -- Deferred G-buffer pass, cascaded shadow mapping with PCF, HDR tone mapping (ACES, Reinhard, AGX, Uncharted2), bloom mip chain and texture atlas packing.

**Animation** -- Clip playback, crossfade blending, multi-layer animation blender, state machine with DSL builder, parameter-driven transitions, animation library scanning and per-bone IK/FK targeting.

**Materials** -- PBR material pipeline with KHR extensions: clearcoat, sheen, transmission, volume, specular, iridescence, anisotropy, emissive strength and unlit mode. Embedded texture support with native image decoding.

**Physics** -- Bullet physics integration with Libbulletjme native bindings. Mesh collision shapes, entity collision tests, cloth grid simulation, hair strand simulation and softbody particle/constraint solving.

**MMD Runtime** -- PMX/PMD model metadata parsing, VMD motion parsing and compilation to AnimClip, VPD pose loading, expression runtime, physics chain builder, toon/stage profile generation, stage camera/light playback, default Minecraft action motion library and project configuration.

**YSM Support** -- Yes Steve Model JSON codec, manifest/geometry/animation schema decoding, bone hierarchy builder, cube mesh generation and directory-based project loading.

**Player Systems** -- Player model project management, asset template installation, player replacement runtime with expression/visibility, vanilla player hiding, item socket binding, equipment socket system and interaction IK selection.

**Network Sync** -- Model state payload capture and apply, remote player interpolation with sample ring buffer, per-player animation parameter blending and multi-player sync engine.

**Module Systems** -- Server policy validation, resource sandbox, online model downloader, model package import/export, action transition editor, replay frame recorder, VR first-person settings, entity replacement rules, block decoration and compatibility layer for VRM, Blockbench, GeckoLib and Figura.

## API

### Format Registry (`cn.chen.blaze3d.api`)

`ModelFeature` -- Feature flags for format capabilities: binary, text, mesh, material, texture, animation, skinning, morph, node, camera, light, physics, MMD and player/scene packages.

`ModelFormat` -- Logical format enum: GLTF, GLB, FBX, OBJ, DAE, MMD, YSM, ASSIMP, OTHER.

`ModelSupport` -- Format descriptor with id, display name, extensions, features and resource types. Provides `supports(path)` for extension checking and `resourcesIn(path)` for sidecar resource discovery.

`ModelFormatProvider` -- Loader contract exposing `support`, `canLoad` and `load` for implementing new format loaders.

`ModelFormatRegistry` -- Runtime registry and dispatcher. Register providers, query by id, detect format by path, and load models into `ModelLoadResult`.

`ResourceIndex` -- Cached file index for fast resource lookup under a root directory with LRU eviction.

`ModelPackage` -- Project package resolution with priority-based main asset detection and resource listing.

`ModelFormats` -- Extension classifier with sets for Assimp, project, resource and sandbox extensions. Handles compound extensions like `.mesh.xml` and `.geo.json`.

### Scene Data (`cn.chen.blaze3d.core`)

`SceneData` -- Loaded scene containing meshes, materials, animations, root node, skeleton and embedded textures.

`MeshData` -- Mesh with vertices, indices, material index, bones and morph targets.

`Vertex` -- Vertex data with position, normal, tangent, bitangent, two UV channels, color, bone IDs and bone weights.

`BonePose` -- Skeleton state holding bone matrices with dirty revision tracking and clip/blend pose computation.

`NodeGraph` -- Scene node tree with transform, mesh indices, children and parent. Recursive lookup and global transform computation.

`AnimClip` -- Animation clip with per-node channels, mesh channels and morph channels. Channels interpolate position, rotation and scale keyframes.

`MorphTarget` -- Morph target position, normal and tangent deltas with blend operations.

`Transform` -- Interface exposing position, scale and rotation.

### Math (`cn.chen.blaze3d.math`)

`Vec3` / `Vec4` -- Mutable 3D/4D vectors with arithmetic, dot, cross, length, normalize, lerp, buffer write and array conversion.

`Quat` -- Quaternion with normalize, slerp, interpolation and matrix conversion.

`Mat4` -- 4x4 float matrix with multiply, inverse, transpose, point/direction transform, JOML conversion and buffer write.

`AABB` -- Axis-aligned bounding box with expand, merge, center, extents, transform, intersection and containment tests.

`Pool` / `PoolScope` / `Slab` -- Thread-local reusable object pools for Vec3, Vec4, Quat and Mat4 with checkpoint/rewind allocation.

### Rendering (`cn.chen.blaze3d.render`)

`WorldRenderer` -- Main renderer and orchestration API managing scene, animator, acceleration, MMD runtime, physics, instances, sockets, audio, IK, player replacement, modules and all render subsystems.

`ModelInstance` -- Transform, visibility, tint, follow and preview state with camera-relative and object matrix builders.

`TextureRegistry` -- Material texture resolver with normal map and alpha pixel queries.

`BatchCompiler` -- Compiles scene data into GPU batches with texture IDs, render pass and AABB.

`GpuSkinning` -- Host-side GPU skinning preparation with dispatch, serial path and vertex encoding.

`GpuCullingDispatch` -- Frustum culling and indirect draw command encoding with cluster visibility testing.

`BoneBuffer` -- CPU/GPU bone matrix buffer with dirty upload and GPU slice access.

`AutoLod` -- Automatic mesh simplification and LOD chain builder with distance-based level selection.

`TextureAtlas` -- Texture atlas packer with UV region remapping.

`Pipelines` -- Render pipeline layouts, vertex formats, shader snippets and pipeline objects.

`DeferredPass` / `DeferredGBuffer` -- Deferred rendering G-buffer management and lighting pass.

`CascadedShadowMap` -- Cascaded shadow map with split calculation, frustum corners and cascade matrix building.

`HdrPipeline` -- HDR composite pipeline with bloom chain, tone mapping (Reinhard, ACES, AGX, Uncharted2) and mip generation.

`VertexCompression` -- Mesh vertex quantization with compression bounds and savings calculation.

`ImageDecoder` -- Native image decoding for texture bytes and raw RGBA data.

### Animation (`cn.chen.blaze3d.anim`)

`Animator` -- Clip playback, crossfade and skeleton pose update with play/stop/pause/resume controls.

`AnimStateMachine` -- Legacy skeleton state machine with named states, conditional transitions and blend progress.

`AnimBlender` -- Multi-layer animation blender with per-layer clip, weight, speed and loop state.

`AnimationStateMachine` -- DSL state machine built with `animationStateMachine { }` builder. Creates `AnimationRuntime` for parameter-driven tick updates.

`AnimationLibrary` -- Motion file discovery scanning for VMD, VPD, BVH, FBX, glTF and other animation formats.

### Physics (`cn.chen.blaze3d.physics`)

`BulletNative` -- Bullet native library loader with platform-specific native extraction.

`BulletWorld` -- Global Bullet physics space lifecycle: init, step and destroy.

`PhysicsBinding` -- Scene physics binding with collision shape building, MMD physics info, transform sync and entity collision.

`PhysicsShape` -- Mesh aggregate collision bounds with sphere/AABB overlap and box building.

`SoftbodyBase` -- Particle and constraint simulation base class. Extended by `ClothSimulation` (grid cloth with pin/unpin/anchor) and `HairSimulation` (multi-strand hair with gravity/wind).

`SoftbodyManager` -- Cloth and hair registration and step management.

### MMD (`cn.chen.blaze3d.mmd`)

`MmdBinary` -- Binary reader for PMX, PMD, VMD and VPD with seek, skip, integer, float, string and index operations.

`MmdVmdParser` / `MmdVpdParser` / `MmdModelMetadataParser` -- Format-specific parsers for motion, pose and model metadata.

`MmdMotionCompiler` -- VMD motion to AnimClip compiler.

`MmdRuntime` -- MMD expression, physics and stage runtime with model binding and update.

`MmdProfiles` -- Physics chain, toon profile and stage profile builder from model metadata.

`MmdActionMapper` -- Local player to MMD action state mapper.

`MmdProjectConfig` -- Project configuration with model, stage, motions, poses, toon, outline, physics, scale and action map.

`MmdDefaultMotionLibrary` -- Built-in Minecraft action motion names and loading.

### YSM (`cn.chen.blaze3d.ysm`)

`YsmCodec` -- JSON codec for YSM manifest, geometry and animation schemas with keyframe and primitive extraction.

`YsmLoader` -- YSM project loader converting directory or file layouts into SceneData.

### Player (`cn.chen.blaze3d.player`)

`PlayerModelProject` -- Player project descriptor with identity, model, stage, motions, poses, scale and toggles.

`PlayerAssetInstaller` -- Template directory creation and player project scanning.

`PlayerReplacementSystem` -- Player model replacement runtime with project selection, renderer binding, expression update and vanilla player hiding.

### Sync (`cn.chen.blaze3d.sync`)

`ModelSync` -- Payload registration, capture and apply helper with toggle and counters.

`ModelSyncEngine` -- Multi-player sync engine with dispatch, tick, remote state lookup and interpolation.

### Advanced (`cn.chen.blaze3d.advanced`)

`AccelerationData` -- BVH acceleration structure with cluster visibility queries and ray intersection.

`BvhRefitter` -- Recomputes cluster and BVH bounds after skeleton pose changes.

`ModuleSystems` -- Aggregates server policy, sandbox, downloader, packages, replay, sockets, VR, replacement, decor, IK, optimizer, compat and preview subsystems.

### LWJGL (`cn.chen.blaze3d.lwjgl`)

`LwjglSdk` -- LWJGL module and platform metadata with Maven artifact coordinate generation.

## Acknowledgments

This project uses [Bazel](https://bazel.build/) as its build system. The Bazel-based Fabric mod build toolchain is inspired by and references [Fabazel](https://github.com/fifth-light/Fabazel) by [fifth-light](https://github.com/fifth-light), which provides the foundational rules for building Minecraft Fabric mods with Bazel.

## License

CC0-1.0
