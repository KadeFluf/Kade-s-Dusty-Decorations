package com.kade.dustydeco; // <-- change to your package

import com.mojang.math.Transformation;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransform;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Drop-in OBJ model loader for Fabric 1.20.1 (Mojang mappings).
 *
 * SETUP (once): in your ClientModInitializer call  ObjModelLoader.init();
 *
 * USAGE:
 *  1. Put the model at  assets/<modid>/models/block/<name>.obj
 *     (do NOT create a models/block/<name>.json next to it)
 *  2. Blockstate points to it like any normal model:
 *        { "variants": { "": { "model": "<modid>:block/<name>" } } }
 *     Variant "y" rotations work.
 *  3. Textures: each `usemtl <material>` maps to the texture
 *        assets/<modid>/textures/block/<material>.png
 *     (write the material as "othermod:foo" to use another namespace; the .mtl file itself is ignored).
 *     Faces with no `usemtl` use  textures/block/<name>.png
 *  4. Block items: nothing to do. If models/item/<name>.json doesn't exist, the item
 *     automatically reuses models/block/<name>.obj. You can also provide models/item/<name>.obj.
 *
 * The OBJ is used as-is: 1 unit = 1 block, and the block occupies 0..1 on each axis.
 * If your model is exported centred on the origin, tweak OFFSET_* below.
 */
public final class ObjModelLoader {

    /** Added to every vertex after scaling (in blocks). Use 0.5 / 0 / 0.5 if the model is centred in Blender. */
    private static final float OFFSET_X = 0.5f, OFFSET_Y = 0f, OFFSET_Z = 0.5f;
    private static final float SCALE = 1f;
    /** OBJ's V axis points up, Minecraft's points down. Leave true. */
    private static final boolean FLIP_V = true;

    private ObjModelLoader() {}

    public static void init() {
        ModelLoadingPlugin.register(plugin -> plugin.resolveModel().register(context -> {
            ResourceLocation id = context.id();
            ResourceLocation obj = findObj(id);
            if (obj == null) return null;
            try {
                return new ObjUnbakedModel(parse(obj), id);
            } catch (Exception e) {
                throw new RuntimeException("Failed to load OBJ model " + obj, e);
            }
        }));
    }

    // ------------------------------------------------------------------ resource lookup

    @Nullable
    private static ResourceLocation findObj(ResourceLocation id) {
        // ModelResourceLocation (blockstate/inventory variants) are handled by vanilla,
        // which then asks for the real "block/x" / "item/x" ids.
        if (id.getPath().contains("#") || id.toString().contains("#")) return null;
        String path = id.getPath();
        ResourceLocation direct = new ResourceLocation(id.getNamespace(), "models/" + path + ".obj");
        if (exists(direct)) return direct;
        if (path.startsWith("item/")) {
            // item json missing -> reuse the block obj
            ResourceLocation asBlock = new ResourceLocation(id.getNamespace(), "models/block/" + path.substring(5) + ".obj");
            ResourceLocation itemJson = new ResourceLocation(id.getNamespace(), "models/" + path + ".json");
            if (!exists(itemJson) && exists(asBlock)) return asBlock;
        }
        return null;
    }

    private static boolean exists(ResourceLocation rl) {
        return Minecraft.getInstance().getResourceManager().getResource(rl).isPresent();
    }

    // ------------------------------------------------------------------ OBJ parsing

    private record Vert(int v, int vt, int vn) {}

    private static final class Face {
        final String material;
        final List<Vert> verts = new ArrayList<>();
        Face(String material) { this.material = material; }
    }

    private static final class ObjData {
        final List<float[]> positions = new ArrayList<>();
        final List<float[]> uvs = new ArrayList<>();
        final List<float[]> normals = new ArrayList<>();
        final List<Face> faces = new ArrayList<>();
        String name;
        String namespace;
    }

    private static ObjData parse(ResourceLocation file) throws Exception {
        ObjData data = new ObjData();
        data.namespace = file.getNamespace();
        String p = file.getPath(); // models/block/foo.obj
        data.name = p.substring(p.lastIndexOf('/') + 1, p.length() - 4);

        Resource res = Minecraft.getInstance().getResourceManager().getResource(file).orElseThrow();
        String material = null;
        try (InputStream in = res.open();
             BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.charAt(0) == '#') continue;
                String[] t = line.split("\\s+");
                switch (t[0]) {
                    case "v" -> data.positions.add(new float[]{f(t[1]), f(t[2]), f(t[3])});
                    case "vt" -> data.uvs.add(new float[]{f(t[1]), t.length > 2 ? f(t[2]) : 0f});
                    case "vn" -> data.normals.add(new float[]{f(t[1]), f(t[2]), f(t[3])});
                    case "usemtl" -> material = t.length > 1 ? t[1] : null;
                    case "f" -> {
                        Face face = new Face(material);
                        for (int i = 1; i < t.length; i++) {
                            String[] s = t[i].split("/", -1);
                            int v = idx(s[0], data.positions.size());
                            int vt = s.length > 1 && !s[1].isEmpty() ? idx(s[1], data.uvs.size()) : -1;
                            int vn = s.length > 2 && !s[2].isEmpty() ? idx(s[2], data.normals.size()) : -1;
                            face.verts.add(new Vert(v, vt, vn));
                        }
                        if (face.verts.size() >= 3) data.faces.add(face);
                    }
                    default -> { /* o, g, s, mtllib ... ignored */ }
                }
            }
        }
        return data;
    }

    private static float f(String s) { return Float.parseFloat(s); }

    /** OBJ indices are 1-based; negative ones are relative to the end. */
    private static int idx(String s, int size) {
        int i = Integer.parseInt(s);
        return i > 0 ? i - 1 : size + i;
    }

    // ------------------------------------------------------------------ unbaked model

    private static final class ObjUnbakedModel implements UnbakedModel {
        private final ObjData data;
        private final ResourceLocation modelId;

        ObjUnbakedModel(ObjData data, ResourceLocation modelId) {
            this.data = data;
            this.modelId = modelId;
        }

        @Override
        public Collection<ResourceLocation> getDependencies() { return Collections.emptyList(); }

        @Override
        public void resolveParents(Function<ResourceLocation, UnbakedModel> getter) { }

        @Nullable
        @Override
        public BakedModel bake(ModelBaker baker, Function<Material, TextureAtlasSprite> spriteGetter,
                               ModelState state, ResourceLocation location) {
            Matrix4f rot = state.getRotation().getMatrix(); // blockstate x/y rotation (identity otherwise)
            boolean rotated = !state.getRotation().equals(Transformation.identity());

            Map<String, TextureAtlasSprite> sprites = new HashMap<>();
            List<BakedQuad> quads = new ArrayList<>();
            TextureAtlasSprite particle = null;

            for (Face face : data.faces) {
                String key = face.material == null ? "" : face.material;
                TextureAtlasSprite sprite = sprites.computeIfAbsent(key, k -> {
                    ResourceLocation tex;
                    if (k.isEmpty()) tex = new ResourceLocation(data.namespace, "block/" + data.name);
                    else if (k.contains(":")) {
                        ResourceLocation m = new ResourceLocation(k);
                        tex = new ResourceLocation(m.getNamespace(), "block/" + m.getPath());
                    } else tex = new ResourceLocation(data.namespace, "block/" + k);
                    return spriteGetter.apply(new Material(TextureAtlas.LOCATION_BLOCKS, tex));
                });
                if (particle == null) particle = sprite;

                List<Vert> vs = face.verts;
                for (int i = 1; i < vs.size() - 1; i++) {
                    // triangle fan; a quad (4 verts) is emitted as one real quad
                    if (vs.size() == 4) {
                        quads.add(buildQuad(sprite, rotated, rot, vs.get(0), vs.get(1), vs.get(2), vs.get(3)));
                        break;
                    }
                    quads.add(buildQuad(sprite, rotated, rot, vs.get(0), vs.get(i), vs.get(i + 1), vs.get(i + 1)));
                }
            }
            if (particle == null) {
                particle = spriteGetter.apply(new Material(TextureAtlas.LOCATION_BLOCKS,
                        new ResourceLocation(data.namespace, "block/" + data.name)));
            }
            return new ObjBakedModel(quads, particle);
        }

        private BakedQuad buildQuad(TextureAtlasSprite sprite, boolean rotated, Matrix4f rot, Vert a, Vert b, Vert c, Vert d) {
            Vert[] vs = {a, b, c, d};
            float[][] pos = new float[4][];
            for (int i = 0; i < 4; i++) {
                float[] p = data.positions.get(vs[i].v());
                pos[i] = new float[]{p[0] * SCALE + OFFSET_X, p[1] * SCALE + OFFSET_Y, p[2] * SCALE + OFFSET_Z};
            }

            // face normal from geometry as fallback
            Vector3f faceN = new Vector3f(pos[1][0] - pos[0][0], pos[1][1] - pos[0][1], pos[1][2] - pos[0][2])
                    .cross(pos[2][0] - pos[0][0], pos[2][1] - pos[0][1], pos[2][2] - pos[0][2]);
            if (faceN.lengthSquared() < 1e-12f) faceN.set(0, 1, 0); else faceN.normalize();

            int[] out = new int[32];
            Vector3f firstNormal = null;
            for (int i = 0; i < 4; i++) {
                Vector3f p = new Vector3f(pos[i][0], pos[i][1], pos[i][2]);
                Vector3f n = vs[i].vn() >= 0
                        ? new Vector3f(data.normals.get(vs[i].vn())[0], data.normals.get(vs[i].vn())[1], data.normals.get(vs[i].vn())[2])
                        : new Vector3f(faceN);
                if (n.lengthSquared() < 1e-12f) n.set(faceN); else n.normalize();

                if (rotated) {
                    p.sub(0.5f, 0.5f, 0.5f);
                    rot.transformPosition(p);
                    p.add(0.5f, 0.5f, 0.5f);
                    rot.transformDirection(n);
                    n.normalize();
                }
                if (firstNormal == null) firstNormal = new Vector3f(n);

                float u = 0, v = 0;
                if (vs[i].vt() >= 0) {
                    float[] uv = data.uvs.get(vs[i].vt());
                    u = uv[0];
                    v = FLIP_V ? 1f - uv[1] : uv[1];
                }

                int o = i * 8;
                out[o] = Float.floatToRawIntBits(p.x);
                out[o + 1] = Float.floatToRawIntBits(p.y);
                out[o + 2] = Float.floatToRawIntBits(p.z);
                out[o + 3] = -1; // white
                out[o + 4] = Float.floatToRawIntBits(sprite.getU(u * 16.0));
                out[o + 5] = Float.floatToRawIntBits(sprite.getV(v * 16.0));
                out[o + 6] = 0; // light, filled in by the renderer
                out[o + 7] = packNormal(n);
            }
            Direction dir = Direction.getNearest(firstNormal.x, firstNormal.y, firstNormal.z);
            return new BakedQuad(out, -1, dir, sprite, true);
        }

        private static int packNormal(Vector3f n) {
            return ((byte) Math.round(n.x * 127f) & 0xFF)
                    | (((byte) Math.round(n.y * 127f) & 0xFF) << 8)
                    | (((byte) Math.round(n.z * 127f) & 0xFF) << 16);
        }
    }

    // ------------------------------------------------------------------ baked model

    private static final class ObjBakedModel implements BakedModel {
        private final List<BakedQuad> quads;
        private final TextureAtlasSprite particle;

        private static final ItemTransforms TRANSFORMS;
        static {
            ItemTransform hand3d = new ItemTransform(new Vector3f(75, 45, 0), new Vector3f(0, 2.5f / 16f, 0), new Vector3f(0.375f));
            ItemTransform hand1st = new ItemTransform(new Vector3f(0, 45, 0), new Vector3f(0, 0, 0), new Vector3f(0.4f));
            ItemTransform gui = new ItemTransform(new Vector3f(30, 225, 0), new Vector3f(0, 0, 0), new Vector3f(0.625f));
            ItemTransform ground = new ItemTransform(new Vector3f(0, 0, 0), new Vector3f(0, 3f / 16f, 0), new Vector3f(0.25f));
            ItemTransform fixed = new ItemTransform(new Vector3f(0, 0, 0), new Vector3f(0, 0, 0), new Vector3f(0.5f));
            TRANSFORMS = new ItemTransforms(hand3d, hand3d, hand1st, hand1st, ItemTransform.NO_TRANSFORM, gui, ground, fixed);
        }

        ObjBakedModel(List<BakedQuad> quads, TextureAtlasSprite particle) {
            this.quads = quads;
            this.particle = particle;
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random) {
            // everything is returned as "uncullable"; no face culling against neighbours
            return side == null ? quads : Collections.emptyList();
        }

        @Override public boolean useAmbientOcclusion() { return true; }
        @Override public boolean isGui3d() { return true; }
        @Override public boolean usesBlockLight() { return true; }
        @Override public boolean isCustomRenderer() { return false; }
        @Override public TextureAtlasSprite getParticleIcon() { return particle; }
        @Override public ItemTransforms getTransforms() { return TRANSFORMS; }
        @Override public ItemOverrides getOverrides() { return ItemOverrides.EMPTY; }
    }
}
