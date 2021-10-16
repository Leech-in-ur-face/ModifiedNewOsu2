package ru.nsu.ccfit.zuev.skins;


import androidx.annotation.NonNull;

import org.json.JSONObject;

import ru.nsu.ccfit.zuev.osu.datatypes.DefaultData;
import ru.nsu.ccfit.zuev.osu.datatypes.IDefaultableData;

public abstract class SkinData<I> implements IDefaultableData<I> {
    protected DefaultData<I> data;
    public String tag;

    public SkinData(String tag, @NonNull DefaultData<I> data) {
        this.tag = tag;
        this.data = data;
    }

    public String getTag() {
        return tag;
    }

    @Override
    public I getDefaultValue() {
        return data.getCurrentValue();
    }

    @Override
    public I getCurrentValue() {
        return data.getCurrentValue();
    }

    @Override
    public void setCurrentValue(I currentValue) {
        data.setCurrentValue(currentValue);
    }

    public abstract void setFromJson(JSONObject data);
}
