package top.fifthlight.armorstand.ui.component

import net.minecraft.client.MinecraftClient
import net.minecraft.client.font.TextRenderer
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.Element
import net.minecraft.client.gui.ScreenRect
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder
import net.minecraft.client.gui.widget.ClickableWidget
import net.minecraft.client.gui.widget.ContainerWidget
import net.minecraft.client.gui.widget.Widget
import net.minecraft.screen.ScreenTexts
import net.minecraft.text.*
import net.minecraft.util.Colors
import net.minecraft.util.Formatting
import top.fifthlight.blazerod.model.Metadata
import java.net.URI
import java.net.URISyntaxException
import java.util.function.Consumer
import kotlin.math.max

class MetadataWidget(
    private val client: MinecraftClient,
    textClickHandler: (Style) -> Unit,
    x: Int = 0,
    y: Int = 0,
    width: Int = 0,
    height: Int = 0,
    metadata: Metadata? = null,
) : ContainerWidget(x, y, width, height, ScreenTexts.EMPTY) {
    companion object {
        private const val GAP = 8
    }

    private val textRenderer = client.textRenderer
    private val entries = listOf<Entry>(
        Entry.TitleAndVersionEntry(textRenderer, textClickHandler),
        Entry.AuthorListEntry(textRenderer, textClickHandler),
        Entry.CopyrightEntry(textRenderer, textClickHandler),
        Entry.LinksEntry(textRenderer, textClickHandler),
        Entry.CommentsEntry(textRenderer, textClickHandler),
        Entry.LicenseEntry(textRenderer, textClickHandler),
        Entry.PermissionsEntry(textRenderer, textClickHandler),
    )

    override fun getContentsHeightWithPadding(): Int = entries.filter { it.visible }.let { entries ->
        entries.sumOf { it.height } + GAP * (entries.size - 1)
    }

    override fun getDeltaYPerScroll(): Double = client.textRenderer.fontHeight * 4.0

    override fun renderWidget(
        context: DrawContext,
        mouseX: Int,
        mouseY: Int,
        deltaTicks: Float,
    ) {
        refreshScroll()
        context.enableScissor(x, y, right, bottom)
        val entryWidth = scrollbarX - 4 - x
        val visibleAreaTop = scrollY.toInt()
        val visibleAreaBottom = scrollY.toInt() + height
        var currentYOffset = 0
        for (entry in entries) {
            if (!entry.visible) {
                continue
            }
            val entryTop = currentYOffset
            val entryBottom = currentYOffset + entry.height
            entry.refreshPositions(x, y + entryTop - visibleAreaTop, entryWidth)
            currentYOffset += entry.height + GAP
            if (entryBottom < visibleAreaTop || entryTop > visibleAreaBottom) {
                continue
            }
            entry.render(
                context,
                mouseX,
                mouseY,
                x,
                y + entryTop - visibleAreaTop,
                entryWidth,
                deltaTicks,
            )
        }
        context.disableScissor()
        drawScrollbar(context)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        for (entry in entries) {
            if (!entry.visible) {
                continue
            }
            if (entry.mouseClicked(mouseX, mouseY, button)) {
                return true
            }
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun appendClickableNarrations(builder: NarrationMessageBuilder) {
        // TODO for better accessibility
    }

    var metadata: Metadata? = metadata
        set(value) {
            field = value
            entries.forEach { it.update(value) }
        }

    override fun children(): List<Element> = entries.filter { it.visible }

    @Suppress("PropertyName")
    private sealed class Entry(
        val textRenderer: TextRenderer,
        val textClickHandler: (Style) -> Unit,
    ) : Element, Widget {
        abstract fun update(metadata: Metadata?)
        abstract val visible: Boolean
        abstract fun refreshPositions(x: Int, y: Int, width: Int)
        protected var _x: Int = 0
        protected var _y: Int = 0
        protected var _width: Int = 0
        protected var _height: Int = 0
        protected var _focused: Boolean = false

        override fun setFocused(focused: Boolean) {
            _focused = focused
        }

        override fun isFocused() = _focused

        override fun setX(x: Int) {
            _x = x
        }

        override fun setY(y: Int) {
            _y = y
        }

        override fun getX() = _x
        override fun getY() = _y
        override fun getWidth() = _width
        override fun getHeight() = _height
        override fun getNavigationFocus(): ScreenRect = super<Widget>.navigationFocus

        override fun forEachChild(consumer: Consumer<ClickableWidget>) = Unit

        abstract fun render(
            context: DrawContext,
            mouseX: Int,
            mouseY: Int,
            x: Int,
            y: Int,
            width: Int,
            deltaTicks: Float,
        )

        class AuthorListEntry(
            textRenderer: TextRenderer,
            textClickHandler: (Style) -> Unit,
            val padding: Int = 8,
            val margin: Int = 8,
            val gap: Int = 8,
            val surface: Surface = Surface.color(0x88000000u) + Surface.border(0xAA000000u),
        ) : Entry(textRenderer, textClickHandler) {
            private class AuthorEntry(
                val name: Text,
                val role: Text? = null,
                val contact: List<Text>,
                val comment: Text? = null,
                var nameHeight: Int = 0,
                var nameWidth: Int = 0,
                var roleWidth: Int = 0,
                var titleLineHeight: Int = 0,
                var contactsLineHeight: IntArray,
                var commentsHeight: Int = 0,
                var totalHeight: Int = 0,
            )

            private var authors: List<AuthorEntry> = listOf()

            override val visible: Boolean
                get() = authors.isNotEmpty()

            override fun update(metadata: Metadata?) {
                authors = metadata?.authors?.map { author ->
                    AuthorEntry(
                        name = Text.literal(author.name),
                        role = author.role?.let { Text.literal(it) },
                        contact = author.contact?.map { (type, value) ->
                            val content = when {
                                value.startsWith("https://", ignoreCase = true) ||
                                        value.startsWith("http://", ignoreCase = true) -> value.urlText()

                                type.equals("email", ignoreCase = true) ||
                                        type.equals("e-mail", ignoreCase = true) -> value.emailText()

                                else -> Text.literal(value)
                            }
                            Text.translatable(
                                "armorstand.metadata.author.contact",
                                type,
                                content,
                            )
                        } ?: listOf(),
                        comment = author.comment?.let { Text.literal(it) },
                        contactsLineHeight = IntArray(author.contact?.size ?: 0),
                    )
                } ?: listOf()
            }

            override fun refreshPositions(x: Int, y: Int, width: Int) {
                val realWidth = width - padding * 2
                var totalHeight = 0
                for ((index, author) in authors.withIndex()) {
                    var authorHeight = 0

                    val role = author.role
                    if (role != null) {
                        val roleWidth = textRenderer.getWidth(role)
                        val nameWidth = realWidth - roleWidth - 8
                        val nameHeight = textRenderer.getWrappedLinesHeight(author.name, nameWidth)
                        val titleLineHeight = max(nameHeight, textRenderer.fontHeight)
                        author.nameWidth = nameWidth
                        author.nameHeight = nameHeight
                        author.roleWidth = roleWidth
                        author.titleLineHeight = titleLineHeight
                        authorHeight += titleLineHeight
                    } else {
                        val nameHeight = textRenderer.getWrappedLinesHeight(author.name, realWidth)
                        val titleLineHeight = max(nameHeight, textRenderer.fontHeight)
                        author.nameWidth = realWidth
                        author.nameHeight = nameHeight
                        author.titleLineHeight = titleLineHeight
                        authorHeight += titleLineHeight
                    }

                    for ((index, contact) in author.contact.withIndex()) {
                        val contactHeight = textRenderer.getWrappedLinesHeight(contact, realWidth)
                        author.contactsLineHeight[index] = contactHeight
                        authorHeight += gap + contactHeight
                    }

                    val comment = author.comment
                    if (comment != null) {
                        val commentHeight = textRenderer.getWrappedLinesHeight(comment, realWidth)
                        author.commentsHeight = commentHeight
                        authorHeight += gap + commentHeight
                    } else {
                        author.commentsHeight = 0
                    }

                    author.totalHeight = authorHeight
                    totalHeight += authorHeight + padding * 2
                    if (index != 0) {
                        totalHeight += margin
                    }
                }
                _x = x
                _y = y
                _width = width
                _height = totalHeight
            }

            override fun render(
                context: DrawContext,
                mouseX: Int,
                mouseY: Int,
                x: Int,
                y: Int,
                width: Int,
                deltaTicks: Float,
            ) {
                val currentX = x + padding
                val realWidth = width - padding * 2
                var currentY = y

                for (author in authors) {
                    surface.draw(context, x, currentY, width, author.totalHeight + padding * 2)
                    currentY += padding
                    context.drawWrappedText(
                        textRenderer,
                        author.name,
                        currentX,
                        currentY,
                        author.nameWidth,
                        Colors.WHITE,
                        false,
                    )
                    author.role?.let { role ->
                        context.drawWrappedText(
                            textRenderer,
                            role,
                            currentX + realWidth - author.roleWidth,
                            currentY,
                            author.roleWidth,
                            Colors.WHITE,
                            false,
                        )
                    }
                    currentY += author.titleLineHeight

                    for ((index, contact) in author.contact.withIndex()) {
                        currentY += gap
                        context.drawWrappedText(
                            textRenderer,
                            contact,
                            currentX,
                            currentY,
                            realWidth,
                            Colors.WHITE,
                            false,
                        )
                        currentY += author.contactsLineHeight[index]
                    }

                    author.comment?.let { comment ->
                        currentY += gap
                        context.drawWrappedText(
                            textRenderer,
                            comment,
                            currentX,
                            currentY,
                            realWidth,
                            Colors.WHITE,
                            false,
                        )
                        currentY += author.commentsHeight
                    }

                    currentY += padding + margin
                }
            }

            override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
                val authors = authors.takeIf { it.isNotEmpty() } ?: return false
                val realWidth = width - padding * 2
                var currentY = 0
                val offsetX = mouseX.toInt() - x
                val offsetY = mouseY.toInt() - y
                if (offsetX !in padding until realWidth) {
                    return false
                }
                if (offsetY !in padding until (height - padding)) {
                    return false
                }

                for (author in authors) {
                    currentY += padding + author.titleLineHeight

                    for ((index, contact) in author.contact.withIndex()) {
                        currentY += gap
                        val contactHeight = author.contactsLineHeight[index]
                        if (offsetY in currentY until currentY + contactHeight) {
                            val lineOffsetY = offsetY - currentY
                            val lineIndex = lineOffsetY / textRenderer.fontHeight
                            val textLines = textRenderer.wrapLines(contact, realWidth)
                            val textLine = textLines.getOrNull(lineIndex) ?: return false
                            val style = textRenderer.textHandler.getStyleAt(textLine, offsetX - padding) ?: return false
                            textClickHandler(style)
                        }
                        currentY += contactHeight
                    }

                    author.comment?.let { comment ->
                        currentY += gap + author.commentsHeight
                    }

                    currentY += padding + margin
                }
                return super.mouseClicked(mouseX, mouseY, button)
            }
        }

        abstract class TextListEntry(
            textRenderer: TextRenderer,
            textClickHandler: (Style) -> Unit,
            val padding: Int = 8,
            val gap: Int = 8,
            val surface: Surface = Surface.color(0x88000000u) + Surface.border(0xAA000000u),
        ) : Entry(textRenderer, textClickHandler) {
            private var texts: List<Text>? = null
            abstract fun getTexts(metadata: Metadata?): List<Text>?

            override val visible: Boolean
                get() = texts?.isNotEmpty() ?: false

            private var textHeights = Array(0) { 0 }
            override fun update(metadata: Metadata?) {
                val newTexts = getTexts(metadata)
                texts = newTexts
                textHeights = Array(newTexts?.size ?: 0) { 0 }
            }

            override fun refreshPositions(x: Int, y: Int, width: Int) {
                var totalHeight = 0
                val realWidth = width - padding * 2
                texts?.let { texts ->
                    for (i in 0 until texts.size) {
                        val textHeight = textRenderer.getWrappedLinesHeight(texts[i], realWidth)
                        textHeights[i] = textHeight
                        totalHeight += textHeight
                    }
                }
                _x = x
                _y = y
                _width = width
                _height = totalHeight + padding * 2 + gap * (texts?.size?.let { it - 1 } ?: 0)
            }

            override fun render(
                context: DrawContext,
                mouseX: Int,
                mouseY: Int,
                x: Int,
                y: Int,
                width: Int,
                deltaTicks: Float,
            ) {
                surface.draw(context, x, y, width, height)
                val realWidth = width - padding * 2
                texts?.let { texts ->
                    var currentY = y + padding
                    for ((index, text) in texts.withIndex()) {
                        context.drawWrappedText(
                            textRenderer,
                            text,
                            x + padding,
                            currentY,
                            realWidth,
                            Colors.WHITE,
                            false,
                        )
                        currentY += textHeights[index] + gap
                    }
                }
            }

            override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
                val texts = texts ?: return false
                if (mouseX.toInt() !in (x + padding) until (x + width - padding)) {
                    return false
                }
                if (mouseY.toInt() !in (y + padding) until (y + height - padding)) {
                    return false
                }

                val offsetX = mouseX.toInt() - (x + padding)
                val offsetY = mouseY.toInt() - (y + padding)
                var textY = 0
                for ((index, height) in textHeights.withIndex()) {
                    if (offsetY in textY until textY + height) {
                        val lineOffsetY = offsetY - textY
                        val lineIndex = lineOffsetY / textRenderer.fontHeight
                        val text = texts[index]
                        val textLines = textRenderer.wrapLines(text, width - padding * 2)
                        val textLine = textLines.getOrNull(lineIndex) ?: return false
                        val style = textRenderer.textHandler.getStyleAt(textLine, offsetX) ?: return false
                        textClickHandler(style)
                        break
                    }
                    textY += height + gap
                }

                return super.mouseClicked(mouseX, mouseY, button)
            }
        }

        class TitleAndVersionEntry(
            textRenderer: TextRenderer,
            textClickHandler: (Style) -> Unit,
        ) : TextListEntry(textRenderer, textClickHandler) {
            override fun getTexts(metadata: Metadata?) = metadata?.let {
                listOfNotNull(
                    metadata.title?.let {
                        Text.translatable("armorstand.metadata.title", metadata.title)
                    } ?: run {
                        Text.translatable("armorstand.metadata.title.unknown")
                    },
                    metadata.version?.let { Text.translatable("armorstand.metadata.version", it) },
                )
            }
        }

        class CopyrightEntry(
            textRenderer: TextRenderer,
            textClickHandler: (Style) -> Unit,
        ) : TextListEntry(textRenderer, textClickHandler) {
            override fun getTexts(metadata: Metadata?) = metadata?.let { metadata ->
                listOfNotNull(
                    metadata.copyrightInformation
                        ?.takeIf(String::isNotBlank)
                        ?.let { copyrightInformation ->
                            Text.translatable(
                                "armorstand.metadata.copyright_information",
                                copyrightInformation
                            )
                        },
                    metadata.references
                        ?.filter { it.isNotBlank() }
                        ?.takeIf(List<String>::isNotEmpty)
                        ?.let { references ->
                            Text.translatable("armorstand.metadata.references", references.joinToString(", "))
                        }
                ).takeIf { list -> list.isNotEmpty() }
            }
        }

        class LinksEntry(
            textRenderer: TextRenderer,
            textClickHandler: (Style) -> Unit,
        ) : TextListEntry(textRenderer, textClickHandler) {
            override fun getTexts(metadata: Metadata?) = listOfNotNull(
                metadata?.linkHome
                    ?.takeIf(String::isNotBlank)
                    ?.let { Text.translatable("armorstand.metadata.link_home", it.urlText()) },
                metadata?.linkDonate
                    ?.takeIf(String::isNotBlank)
                    ?.let { Text.translatable("armorstand.metadata.link_donate", it.urlText()) },
            )
        }

        class CommentsEntry(
            textRenderer: TextRenderer,
            textClickHandler: (Style) -> Unit,
        ) : TextListEntry(textRenderer, textClickHandler) {
            override fun getTexts(metadata: Metadata?) = listOfNotNull(
                metadata?.comment
                    ?.takeIf(String::isNotBlank)
                    ?.replace("\r\n", "\n")
                    ?.let { Text.translatable("armorstand.metadata.comments", it) }
            )
        }

        class LicenseEntry(
            textRenderer: TextRenderer,
            textClickHandler: (Style) -> Unit,
        ) : TextListEntry(textRenderer, textClickHandler) {
            override fun getTexts(metadata: Metadata?) = listOfNotNull(
                metadata?.licenseType
                    ?.takeIf(String::isNotBlank)
                    ?.let { Text.translatable("armorstand.metadata.license_type", it) },
                metadata?.licenseDescription
                    ?.takeIf(String::isNotBlank)
                    ?.let { Text.translatable("armorstand.metadata.license_description", it) },
                metadata?.licenseUrl
                    ?.takeIf(String::isNotBlank)
                    ?.let { Text.translatable("armorstand.metadata.license_url", it.urlText()) },
                metadata?.thirdPartyLicenses
                    ?.takeIf(String::isNotBlank)
                    ?.let { Text.translatable("armorstand.metadata.third_party_licenses", it) },
                metadata?.specLicenseUrl
                    ?.takeIf(String::isNotBlank)
                    ?.let { Text.translatable("armorstand.metadata.spec_license_url", it.urlText()) },
            )
        }

        class PermissionsEntry(
            textRenderer: TextRenderer,
            textClickHandler: (Style) -> Unit,
        ) : TextListEntry(textRenderer, textClickHandler) {
            override fun getTexts(metadata: Metadata?) = listOfNotNull(
                metadata?.allowedUser?.let {
                    when (it) {
                        Metadata.AllowedUser.ONLY_AUTHOR -> Text.translatable("armorstand.metadata.allowed_user.only_author")
                        Metadata.AllowedUser.EXPLICITLY_LICENSED_PERSON -> Text.translatable("armorstand.metadata.allowed_user.explicitly_licensed_person")
                        Metadata.AllowedUser.EVERYONE -> Text.translatable("armorstand.metadata.allowed_user.everyone")
                    }
                },
                metadata?.allowViolentUsage?.let {
                    if (it) {
                        Text.translatable("armorstand.metadata.violent_usage.allow")
                    } else {
                        Text.translatable("armorstand.metadata.violent_usage.disallow")
                    }
                },
                metadata?.allowSexualUsage?.let {
                    if (it) {
                        Text.translatable("armorstand.metadata.sexual_usage.allow")
                    } else {
                        Text.translatable("armorstand.metadata.sexual_usage.disallow")
                    }
                },
                metadata?.commercialUsage?.let {
                    when (it) {
                        Metadata.CommercialUsage.DISALLOW -> Text.translatable("armorstand.metadata.commercial_usage.disallow")
                        Metadata.CommercialUsage.ALLOW -> Text.translatable("armorstand.metadata.commercial_usage.allow")
                        Metadata.CommercialUsage.PERSONAL_NON_PROFIT -> Text.translatable("armorstand.metadata.commercial_usage.personal_non_profit")
                        Metadata.CommercialUsage.PERSONAL_PROFIT -> Text.translatable("armorstand.metadata.commercial_usage.personal_profit")
                        Metadata.CommercialUsage.CORPORATION -> Text.translatable("armorstand.metadata.commercial_usage.corporation")
                    }
                },
                metadata?.allowPoliticalOrReligiousUsage?.let {
                    if (it) {
                        Text.translatable("armorstand.metadata.political_or_religious_usage.allow")
                    } else {
                        Text.translatable("armorstand.metadata.political_or_religious_usage.disallow")
                    }
                },
                metadata?.allowAntisocialOrHateUsage?.let {
                    if (it) {
                        Text.translatable("armorstand.metadata.antisocial_or_hate_usage.allow")
                    } else {
                        Text.translatable("armorstand.metadata.antisocial_or_hate_usage.disallow")
                    }
                },
                metadata?.creditNotation?.let {
                    when (it) {
                        Metadata.CreditNotation.REQUIRED -> Text.translatable("armorstand.metadata.credit_notation.required")
                        Metadata.CreditNotation.UNNECESSARY -> Text.translatable("armorstand.metadata.credit_notation.unnecessary")
                    }
                },
                metadata?.allowRedistribution?.let {
                    if (it) {
                        Text.translatable("armorstand.metadata.redistribution.allow")
                    } else {
                        Text.translatable("armorstand.metadata.redistribution.disallow")
                    }
                },
                metadata?.modificationPermission?.let {
                    when (it) {
                        Metadata.ModificationPermission.PROHIBITED -> Text.translatable("armorstand.metadata.modification_permission.prohibited")
                        Metadata.ModificationPermission.ALLOW_MODIFICATION -> Text.translatable("armorstand.metadata.modification_permission.allow_modification")
                        Metadata.ModificationPermission.ALLOW_MODIFICATION_REDISTRIBUTION -> Text.translatable("armorstand.metadata.modification_permission.allow_modification_redistribution")
                    }
                },
                metadata?.permissionUrl
                    ?.takeIf(String::isNotBlank)
                    ?.let { Text.translatable("armorstand.metadata.permission_url", it.urlText()) },
            )
        }
    }
}

private fun String.urlText(uri: URI): Text = MutableText
    .of(PlainTextContent.of(this))
    .setStyle(
        Style.EMPTY
            .withFormatting(Formatting.BLUE)
            .withUnderline(true)
            .withClickEvent(ClickEvent.OpenUrl(uri))
    )

private fun String.emailText(): Text {
    val uri = try {
        URI("mailto:$this")
    } catch (e: URISyntaxException) {
        return Text.of(this)
    }
    return MutableText.of(PlainTextContent.of(this)).setStyle(
        Style.EMPTY
            .withFormatting(Formatting.BLUE)
            .withUnderline(true)
            .withClickEvent(ClickEvent.OpenUrl(uri))
    )
}

private fun String.urlText() = try {
    urlText(URI(this))
} catch (e: URISyntaxException) {
    Text.of(this)
}
