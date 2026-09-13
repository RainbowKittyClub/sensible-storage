package club.rainbowkitty.sensiblestorage.books;

import java.util.List;
import java.util.stream.Stream;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import club.rainbowkitty.kalic0re.common.label.ItemText;
import club.rainbowkitty.kalic0re.common.label.LabelText;

/** Labels a book with its title, styled so its type reads at a glance, over what it holds. */
public final class BookText {
    private static final ChatFormatting UNNAMED = ChatFormatting.GRAY;

    private BookText() {
    }

    /** Claims books away from the generic item text. */
    public static void register() {
        ItemText.register(BookText::of);
    }

    /** The label for a book, or null for anything else so the generic form takes over. */
    public static LabelText of(ItemStack stack) {
        if (!stack.typeHolder().is(ItemTags.BOOKSHELF_BOOKS)) {
            return null;
        }
        return new LabelText(heading(stack), subheadings(stack));
    }

    private static Component heading(ItemStack book) {
        // A draft stays dim whether or not it has been titled; italics mark the title as the
        // author's working one rather than a published one.
        if (book.getItem() == Items.WRITABLE_BOOK) {
            MutableComponent draft = Component.empty().withStyle(UNNAMED);
            return (book.getCustomName() != null ? draft.withStyle(ChatFormatting.ITALIC) : draft)
                    .append(book.getHoverName());
        }

        return ItemText.heading(book);
    }

    // An enchanted book carries its enchantments as STORED_ENCHANTMENTS, which the generic form
    // does not read, so they go above whatever it did find.
    private static List<Component> subheadings(ItemStack book) {
        ItemEnchantments stored = book.get(DataComponents.STORED_ENCHANTMENTS);
        return Stream.concat(ItemText.enchantmentLines(stored).stream(),
                ItemText.subheadings(book).stream()).toList();
    }
}
