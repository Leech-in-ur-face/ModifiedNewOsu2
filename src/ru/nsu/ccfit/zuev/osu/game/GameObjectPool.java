package ru.nsu.ccfit.zuev.osu.game;

import ru.nsu.ccfit.zuev.osu.Config;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

public class GameObjectPool {

    public static final GameObjectPool instance = new GameObjectPool();

    public final LinkedList<HitCircle> circles = new LinkedList<>();

    public final Map<Integer, LinkedList<CircleNumber>> numbers = new HashMap<>();

    public final Map<String, LinkedList<GameEffect>> effects = new HashMap<>();

    public final LinkedList<Slider> sliders = new LinkedList<>();

    public final LinkedList<FollowTrack> tracks = new LinkedList<>();

    public final LinkedList<Spinner> spinners = new LinkedList<>();

    private int objectsCreated = 0;

    private GameObjectPool() {
    }

    public static GameObjectPool getInstance() {
        return instance;
    }

    public HitCircle getCircle() {
        if (!circles.isEmpty()) {
            return circles.poll();
        }

        objectsCreated++;
        return new HitCircle();
    }

    public void putCircle(final HitCircle circle) {
        circles.add(circle);
    }

    public Spinner getSpinner() {
        if (!spinners.isEmpty()) {
            return spinners.poll();
        }

        objectsCreated++;
        if (Config.getSpinnerStyle() == 1) {
            return new ModernSpinner();
        } else {
            return new Spinner();
        }
    }

    public CircleNumber getNumber(final int num) {
        if (numbers.containsKey(num) && !numbers.get(num).isEmpty()) {
            return numbers.get(num).poll();
        }

        objectsCreated++;
        return new CircleNumber(num);
    }

    public void putNumber(final CircleNumber number) {
        if (!numbers.containsKey(number.getNum())) {
            numbers.put(number.getNum(), new LinkedList<>());
        }
        numbers.get(number.getNum()).add(number);
    }

    public GameEffect getEffect(final String texname) {
        if (effects.containsKey(texname) && !effects.get(texname).isEmpty()) {
            return effects.get(texname).poll();
        }

        objectsCreated++;
        return new GameEffect(texname);
    }

    public void putEffect(final GameEffect effect) {
        if (!effects.containsKey(effect.getTexname())) {
            effects.put(effect.getTexname(), new LinkedList<>());
        }
        effects.get(effect.getTexname()).add(effect);
    }

    public Slider getSlider() {
        if (!sliders.isEmpty()) {
            return sliders.poll();
        }

        objectsCreated++;
        return new Slider();
    }

    public void putSlider(final Slider slider) {
        sliders.add(slider);
    }

    public FollowTrack getTrack() {
        if (!tracks.isEmpty()) {
            return tracks.poll();
        }

        objectsCreated++;
        return new FollowTrack();
    }

    public void putTrac(final FollowTrack track) {
        tracks.add(track);
    }

    public void purge() {
        effects.clear();
        circles.clear();
        numbers.clear();
        sliders.clear();
        tracks.clear();
        objectsCreated = 0;
    }

    public void preload() {
        for (int i = 0; i < 10; i++) {
            putCircle(new HitCircle());
            putNumber(new CircleNumber(i + 1));
        }
        for (int i = 0; i < 5; i++) {
            putSlider(new Slider());
            putTrac(new FollowTrack());
        }
        new Spinner();
        objectsCreated = 31;
    }

}
