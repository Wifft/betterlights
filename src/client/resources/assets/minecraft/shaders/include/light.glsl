#ifndef MINECRAFT_LIGHT_GLSL
#define MINECRAFT_LIGHT_GLSL

#define MINECRAFT_LIGHT_POWER   (0.6)
#define MINECRAFT_AMBIENT_LIGHT (0.4)

layout(std140) uniform Lighting {
    vec3 Light0_Direction;
    vec3 Light1_Direction;
    vec4 CardinalA; // down, up, north, south
    vec4 CardinalB; // west, east, per-face flag (1 for the level, 0 for GUI/items), unused
};

vec2 minecraft_compute_light(vec3 lightDir0, vec3 lightDir1, vec3 normal) {
    return vec2(dot(lightDir0, normal), dot(lightDir1, normal));
}

vec4 minecraft_mix_light_separate(vec2 light, vec4 color) {
    vec2 lightValue = max(vec2(0.0), light);
    float lightAccum = min(1.0, (lightValue.x + lightValue.y) * MINECRAFT_LIGHT_POWER + MINECRAFT_AMBIENT_LIGHT);
    return vec4(color.rgb * lightAccum, color.a);
}

vec4 minecraft_mix_light(vec3 lightDir0, vec3 lightDir1, vec3 normal, vec4 color) {
    vec2 light = minecraft_compute_light(lightDir0, lightDir1, normal);
    return minecraft_mix_light_separate(light, color);
}

// Per-face brightness table (CardinalLighting) weighted by the squared normal:
// exact for axis-aligned faces, smooth in between. -X = west, +X = east, -Z = north, +Z = south.
float minecraft_cardinal_shade(vec3 normal) {
    if (dot(normal, normal) < 1.0e-6) {
        return 1.0;
    }
    vec3 n = normalize(normal);
    vec3 w = n * n;
    float x = n.x > 0.0 ? CardinalB.y : CardinalB.x;
    float y = n.y > 0.0 ? CardinalA.y : CardinalA.x;
    float z = n.z > 0.0 ? CardinalA.w : CardinalA.z;
    return w.x * x + w.y * y + w.z * z;
}

// Unified lighting for terrain and block models: per-face shading in the level, vanilla two-light shading elsewhere.
// Entities and items keep the vanilla shaders (two lights).
vec4 minecraft_terrain_light(vec3 normal, vec4 color) {
    if (CardinalB.z > 0.5) {
        return vec4(color.rgb * minecraft_cardinal_shade(normal), color.a);
    }
    return minecraft_mix_light(Light0_Direction, Light1_Direction, normal, color);
}

#endif
