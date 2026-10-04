package info.rusty.webshoplink;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
//? if >=1.20.5 {
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
//?}

/**
 * An item stack's custom data as the single {@link CompoundTag} the shop API exchanges (the
 * {@code nbt} field of an item), on every Minecraft version.
 * <p>
 * Up to 1.20.4 that is the stack's NBT tag. From 1.20.5 on, item NBT was replaced by data
 * components, so it is the stack's component patch (what differs from the item's defaults),
 * encoded to NBT: keys are component ids such as {@code minecraft:custom_data} or
 * {@code minecraft:damage}, the same form {@code /give} accepts. The shop backend therefore sees
 * a different, version-specific shape for the same item on a 1.21 server.
 */
public final class StackNbt {

    private StackNbt() {
    }

    public static boolean has(ItemStack stack) {
        //? if >=1.20.5 {
        return !stack.getComponentsPatch().isEmpty();
        //?} else
        /*return stack.hasTag();*/
    }

    /** The stack's custom data, or {@code null} when it has none. */
    @Nullable
    public static CompoundTag get(ItemStack stack) {
        //? if >=1.20.5 {
        DataComponentPatch patch = stack.getComponentsPatch();
        if (patch.isEmpty()) {
            return null;
        }
        Tag tag = DataComponentPatch.CODEC.encodeStart(registries().createSerializationContext(NbtOps.INSTANCE), patch)
                .getOrThrow(IllegalStateException::new);
        return tag instanceof CompoundTag compound ? compound : null;
        //?} else
        /*return stack.getTag();*/
    }

    /**
     * Replaces the stack's custom data with {@code tag}; {@code null} clears it. Only ever called
     * on fresh stacks, so on 1.20.5+ applying the patch on top of the item defaults is a replace.
     */
    public static void set(ItemStack stack, @Nullable CompoundTag tag) {
        //? if >=1.20.5 {
        if (tag == null) {
            return;
        }
        DataComponentPatch patch = DataComponentPatch.CODEC.parse(registries().createSerializationContext(NbtOps.INSTANCE), tag)
                .getOrThrow(IllegalArgumentException::new);
        stack.applyComponents(patch);
        //?} else
        /*stack.setTag(tag);*/
    }
    //? if >=1.20.5 {

    // Components such as enchantments reference registries, so encoding needs the server's.
    private static HolderLookup.Provider registries() {
        MinecraftServer server = Webshoplink.server();
        return server != null ? server.registryAccess() : RegistryAccess.EMPTY;
    }
    //?}
}
