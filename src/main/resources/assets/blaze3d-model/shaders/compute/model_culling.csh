#version 460
layout(local_size_x = 64, local_size_y = 1, local_size_z = 1) in;
struct ClusterAABB { vec4 mn; vec4 mx; uvec4 meta; };
struct DrawCommand { uint indexCount; uint instanceCount; uint firstIndex; uint baseVertex; uint baseInstance; };
layout(std430, binding = 0) readonly buffer InputClusters { ClusterAABB clusters[]; };
layout(std430, binding = 1) buffer OutCommands { DrawCommand commands[]; };
layout(std430, binding = 2) buffer Counter { uint drawCount; };
layout(std140, binding = 3) uniform CullingParams {
    vec4 frustumPlanes[6];
    vec4 cameraPos;
    vec4 lodThresholds;
    uvec4 clusterStrides;
};
bool intersectsFrustum(vec3 mn, vec3 mx) {
    for (int i = 0; i < 6; ++i) {
        vec4 plane = frustumPlanes[i];
        vec3 positive = vec3(plane.x > 0.0 ? mx.x : mn.x, plane.y > 0.0 ? mx.y : mn.y, plane.z > 0.0 ? mx.z : mn.z);
        if (dot(plane.xyz, positive) + plane.w < 0.0) return false;
    }
    return true;
}
void main() {
    uint id = gl_GlobalInvocationID.x;
    if (id >= clusterStrides.x) return;
    ClusterAABB cluster = clusters[id];
    vec3 mn = cluster.mn.xyz; vec3 mx = cluster.mx.xyz;
    if (!intersectsFrustum(mn, mx)) return;
    vec3 center = (mn + mx) * 0.5;
    float distance = distance(center, cameraPos.xyz);
    uint lodLevel = cluster.meta.w;
    uint target = (distance > lodThresholds.x) ? 2u : (distance > lodThresholds.y) ? 1u : 0u;
    if (lodLevel < target) return;
    uint slot = atomicAdd(drawCount, 1u);
    if (slot >= clusterStrides.y) return;
    DrawCommand cmd;
    cmd.indexCount = cluster.meta.x; cmd.instanceCount = 1u; cmd.firstIndex = cluster.meta.y; cmd.baseVertex = cluster.meta.z; cmd.baseInstance = 0u;
    commands[slot] = cmd;
}
