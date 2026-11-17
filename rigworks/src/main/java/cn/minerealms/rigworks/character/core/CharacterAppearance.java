package cn.minerealms.rigworks.character.core;

import java.util.Objects;

/** Small visual identity; asset bytes and executable scripts are never sent over this protocol. */
public record CharacterAppearance(boolean enabled, String profile, String mode, boolean flying, long revision) {
    public CharacterAppearance {
        Objects.requireNonNull(profile);
        if (profile.length() > 256 || !profile.matches("[a-z0-9_.-]+:[a-z0-9_.-][a-z0-9_./-]*")
                || profile.contains("..")) throw new IllegalArgumentException("Invalid character profile resource");
        if (!Objects.equals(mode, "VANILLA") && !Objects.equals(mode, "CLIPS") && !Objects.equals(mode, "SCRIPTED"))
            throw new IllegalArgumentException("Unknown character animation mode");
        if (revision < 0) throw new IllegalArgumentException("Negative appearance revision");
    }

    public boolean sameSelection(CharacterAppearance other) {
        return other != null && enabled == other.enabled && profile.equals(other.profile)
                && mode.equals(other.mode) && flying == other.flying;
    }
}
