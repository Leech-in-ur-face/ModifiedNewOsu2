package ru.nsu.ccfit.zuev.osu.game.cursor;


import android.view.animation.AlphaAnimation;

import org.anddev.andengine.entity.Entity;
import org.anddev.andengine.entity.modifier.AlphaModifier;
import org.anddev.andengine.entity.sprite.Sprite;
import org.anddev.andengine.opengl.texture.region.TextureRegion;
import org.anddev.andengine.util.Debug;

import javax.microedition.khronos.opengles.GL10;

import ru.nsu.ccfit.zuev.osu.ResourceManager;

public class FlashLightDimLayer extends Entity {
    private final Sprite sprite;

    public FlashLightDimLayer() {
        TextureRegion tex = ResourceManager.getInstance().getTexture("flashlight_dim_layer");
        sprite = new Sprite(-tex.getWidth() / 2f, -tex.getHeight() / 2f, tex);
        float size = 8f;
        sprite.setScale(size);
        attachChild(sprite);
    }

    public void update(boolean isSliderHold) {
        sprite.setVisible(isSliderHold);
    }
}
