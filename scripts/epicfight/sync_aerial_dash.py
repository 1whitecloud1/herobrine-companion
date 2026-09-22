"""Sync only the new sprint slot and its runtime class to the Forge project."""
import hashlib
import json
import shutil
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]
TARGET=ROOT.parent/'herobrine companion'
RES=Path('src/main/resources/assets/herobrine_companion')
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
for relative in (RES/'animmodels/animations/player/poem_mediapipe/dash.json',
                 RES/'epicfight/poem_mediapipe_timing.json'):
    shutil.copy2(ROOT/relative,TARGET/relative)
    assert sha(ROOT/relative)==sha(TARGET/relative)
relative=Path('src/main/java/com/whitecloud233/herobrine_companion/compat/epicfight/MediaPipeScytheDashAnimation.java')
target=TARGET/str(relative).replace('whitecloud233\\herobrine','whitecloud233\\modid\\herobrine')
target.write_text((ROOT/relative).read_text(encoding='utf-8').replace(
    'package com.whitecloud233.herobrine_companion','package com.whitecloud233.modid.herobrine_companion'),encoding='utf-8')
check=Path('scripts/epicfight/AerialDashRuntimeCheck.java')
ported=(ROOT/check).read_text(encoding='utf-8').replace(
    'package com.whitecloud233.herobrine_companion','package com.whitecloud233.modid.herobrine_companion').replace(
    'private ArmatureFixture() { super(null); }','private ArmatureFixture() { super(); }')
# Forge hashes animation files in the constructor; expose the real assets to
# its ResourceManager without booting ModList, as the existing registry check does.
ported=ported.replace('import net.minecraft.world.entity.LivingEntity;',
    'import net.minecraft.world.entity.LivingEntity;\nimport net.minecraft.server.packs.PackType;\n'
    'import net.minecraft.server.packs.PathPackResources;\n'
    'import net.minecraft.server.packs.resources.MultiPackResourceManager;')
ported=ported.replace('public static void main(String[] args) throws Exception {',
    '''public static void main(String[] args) throws Exception {
        try (var resources = new MultiPackResourceManager(PackType.CLIENT_RESOURCES,
                List.of(new PathPackResources("aerial-dash-verifier", Path.of("src/main/resources"), false)))) {
            AnimationManager.setServerResourceManager(resources);
            verify();
        } finally {
            AnimationManager.setServerResourceManager(null);
        }
    }

    private static void verify() throws Exception {''')
(TARGET/check).write_text(ported,encoding='utf-8')
print('AERIAL_DASH_FORGE_SYNC',target)
