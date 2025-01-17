/*
 * Copyright (c) 2020 - 2022 LambdAurora <email@lambdaurora.dev>, Emi
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package io.github.queerbric.inspecio.tooltip;

import com.mojang.blaze3d.lighting.DiffuseLighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.texture.NativeImage;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.queerbric.inspecio.Inspecio;
import io.github.queerbric.inspecio.SignTooltipMode;
import net.minecraft.block.AbstractSignBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.gui.tooltip.TooltipComponent;
import net.minecraft.client.item.TooltipData;
import net.minecraft.client.model.Model;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.TexturedRenderLayers;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.HangingSignBlockEntityRenderer;
import net.minecraft.client.render.block.entity.SignBlockEntityRenderer;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.util.SpriteIdentifier;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.BlockItem;
import net.minecraft.item.HangingSignItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SignItem;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Identifier;
import net.minecraft.util.SignType;
import org.joml.Matrix4f;
import org.quiltmc.qsl.tooltip.api.ConvertibleTooltipData;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;

public abstract class SignTooltipComponent<M extends Model> implements ConvertibleTooltipData, TooltipComponent {
	protected static final MinecraftClient CLIENT = MinecraftClient.getInstance();
	private final SignTooltipMode tooltipMode = Inspecio.getConfig().getSignTooltipMode();
	protected final SignType type;
	private final OrderedText[] text;
	private final DyeColor color;
	private final boolean glowingText;
	protected final M model;

	public SignTooltipComponent(SignType type, OrderedText[] text, DyeColor color, boolean glowingText, M model) {
		this.type = type;
		this.text = text;
		this.color = color;
		this.glowingText = glowingText;
		this.model = model;
	}

	public static Optional<TooltipData> fromItemStack(ItemStack stack) {
		if (!Inspecio.getConfig().getSignTooltipMode().isEnabled())
			return Optional.empty();

		if (stack.getItem() instanceof SignItem signItem) {
			var block = signItem.getBlock();
			var nbt = BlockItem.getBlockEntityNbtFromStack(stack);
			if (nbt != null) return Optional.of(fromTag(AbstractSignBlock.getSignType(block), nbt, false));
		} else if (stack.getItem() instanceof HangingSignItem signItem) {
			var block = signItem.getBlock();
			var nbt = BlockItem.getBlockEntityNbtFromStack(stack);
			if (nbt != null) return Optional.of(fromTag(AbstractSignBlock.getSignType(block), nbt, true));
		}
		return Optional.empty();
	}

	public static SignTooltipComponent<?> fromTag(SignType type, NbtCompound nbt, boolean hanging) {
		var color = DyeColor.byName(nbt.getString("Color"), DyeColor.BLACK);

		var lines = new OrderedText[4];
		for (int i = 0; i < 4; ++i) {
			var serialized = nbt.getString("Text" + (i + 1));
			var text = Objects.requireNonNull(Text.Serializer.fromJson(serialized.isEmpty() ? "\"\"" : serialized))
					.asOrderedText();
			lines[i] = text;
		}

		boolean glowingText = nbt.getBoolean("GlowingText");

		if (hanging) {
			return new HangingSign(type, lines, color, glowingText);
		} else {
			return new Sign(type, lines, color, glowingText);
		}
	}

	@Override
	public TooltipComponent toComponent() {
		return this;
	}

	@Override
	public int getHeight() {
		if (this.tooltipMode == SignTooltipMode.FANCY)
			return this.getFancyHeight();
		return this.text.length * 10;
	}

	protected abstract int getFancyHeight();

	@Override
	public int getWidth(TextRenderer textRenderer) {
		if (this.tooltipMode == SignTooltipMode.FANCY)
			return this.getFancyWidth();
		return Arrays.stream(this.text).map(textRenderer::getWidth).max(Comparator.naturalOrder()).orElse(94);
	}

	protected abstract int getFancyWidth();

	@Override
	public void drawText(TextRenderer textRenderer, int x, int y, Matrix4f matrix4f, VertexConsumerProvider.Immediate immediate) {
		if (this.tooltipMode != SignTooltipMode.FAST)
			return;

		this.drawTextAt(textRenderer, x, y, matrix4f, immediate, false);
	}

	public void drawTextAt(TextRenderer textRenderer, int x, int y, Matrix4f matrix4f, VertexConsumerProvider.Immediate immediate, boolean center) {
		int signColor = this.color.getSignColor();

		if (glowingText) {
			int outlineColor;
			if (this.color == DyeColor.BLACK) {
				outlineColor = -988212;
			} else {
				int r = (int) (NativeImage.getRed(signColor) * 0.4);
				int g = (int) (NativeImage.getGreen(signColor) * 0.4);
				int b = (int) (NativeImage.getBlue(signColor) * 0.4);

				outlineColor = NativeImage.getAbgrColor(0, b, g, r);
			}

			for (int i = 0; i < this.text.length; i++) {
				var text = this.text[i];
				float textX = center ? (45 - textRenderer.getWidth(text) / 2.f) : x;
				textRenderer.drawWithOutline(text, textX, y + i * 10, signColor, outlineColor, matrix4f, immediate,
						LightmapTextureManager.MAX_LIGHT_COORDINATE
				);
			}
		} else {
			for (int i = 0; i < this.text.length; i++) {
				var text = this.text[i];
				float textX = center ? (45 - textRenderer.getWidth(text) / 2.f) : x;
				textRenderer.m_ldakjnum(text, textX, y + i * 10, signColor, false, matrix4f, immediate, false,
						0, LightmapTextureManager.MAX_LIGHT_COORDINATE);
			}
		}
	}

	@Override
	public void drawItems(TextRenderer textRenderer, int x, int y, MatrixStack matrices, ItemRenderer itemRenderer, int z) {
		if (this.tooltipMode != SignTooltipMode.FANCY)
			return;

		DiffuseLighting.setupFlatGuiLighting();
		matrices.push();
		matrices.translate(x + 2, y, z);

		matrices.push();
		var immediate = CLIENT.getBufferBuilders().getEntityVertexConsumers();
		var spriteIdentifier = this.getSignTextureId();
		var vertexConsumer = spriteIdentifier != null ? spriteIdentifier.getVertexConsumer(immediate, this.model::getLayer) : null;
		this.renderModel(matrices, vertexConsumer);
		immediate.draw();
		matrices.pop();

		matrices.translate(0, this.getTextOffset(), 10);

		for (int i = 0; i < this.text.length; i++) {
			var text = this.text[i];
			textRenderer.draw(matrices, text, 45 - textRenderer.getWidth(text) / 2.f, i * 10, this.color.getSignColor());
		}
		matrices.pop();

		DiffuseLighting.setup3DGuiLighting();
	}

	public abstract SpriteIdentifier getSignTextureId();

	public abstract void renderModel(MatrixStack matrices, VertexConsumer vertexConsumer);

	/**
	 * {@return the vertical offset between the start of the component and where the text lines should be drawn}
	 */
	protected abstract int getTextOffset();

	public static class Sign extends SignTooltipComponent<SignBlockEntityRenderer.SignModel> {

		public Sign(SignType type, OrderedText[] text, DyeColor color, boolean glowingText) {
			super(type, text, color, glowingText,
					SignBlockEntityRenderer.createSignModel(CLIENT.getEntityModelLoader(), type)
			);
		}

		@Override
		protected int getFancyHeight() {
			return 52;
		}

		@Override
		protected int getFancyWidth() {
			return 94;
		}

		@Override
		public SpriteIdentifier getSignTextureId() {
			return TexturedRenderLayers.getSignTextureId(this.type);
		}

		@Override
		public void renderModel(MatrixStack matrices, VertexConsumer vertexConsumer) {
			matrices.translate(45, 56, 0);
			matrices.scale(65, 65, -65);
			this.model.stick.visible = false;
			this.model.root.visible = true;
			this.model.root.render(matrices, vertexConsumer, LightmapTextureManager.MAX_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV);
		}

		@Override
		protected int getTextOffset() {
			return 4;
		}
	}

	public static class HangingSign extends SignTooltipComponent<HangingSignBlockEntityRenderer.HangingSignModel> {
		private final Identifier textureId = new Identifier("textures/gui/hanging_signs/" + this.type.getName() + ".png");

		public HangingSign(SignType type, OrderedText[] text, DyeColor color, boolean glowingText) {
			super(type, text, color, glowingText, null);
		}

		@Override
		protected int getFancyHeight() {
			return 68;
		}

		@Override
		protected int getFancyWidth() {
			return 94;
		}

		@Override
		public SpriteIdentifier getSignTextureId() {
			return null;
		}

		@Override
		public void renderModel(MatrixStack matrices, VertexConsumer vertexConsumer) {
			matrices.translate(44.5, 32, 0);
			RenderSystem.setShaderTexture(0, this.textureId);
			RenderSystem.setShaderColor(1.f, 1.f, 1.f, 1.f);
			matrices.scale(4.f, 4.f, 1.f);
			DrawableHelper.drawTexture(matrices, -8, -8, 0.f, 0.f, 16, 16, 16, 16);
		}

		@Override
		protected int getTextOffset() {
			return 26;
		}
	}
}
