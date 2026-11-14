package cn.minerealms.assimploader.showcase;

public enum ShowcaseModel {
    CIWS_MATERIAL("ciws_material", "CIWS", "Material colors"),
    CIWS_TEXTURED("ciws_textured", "CIWS", "Embedded texture"),
    KNIFE_MATERIAL("knife_material", "Knife", "Material colors"),
    KNIFE_TEXTURED("knife_textured", "Knife", "Embedded texture"),
    PROBE_NO_UV("probe_no_uv", "Axis probe", "No UV / no texture"),
    PROBE_TEXTURED("probe_textured", "Axis probe", "Embedded texture"),
    PBR_PROBE("pbr_probe", "PBR materials", "Matte / polished / metal"),
    PORTAL_PBR("portal_pbr", "Sci-fi portal", "Metal / glass / normal maps"),
    ROCKET_ENGINE("rocket_engine", "Rocket engine", "PBR / volumetric exhaust");

    private final String modelName;
    private final String title;
    private final String subtitle;

    ShowcaseModel(String modelName, String title, String subtitle) {
        this.modelName = modelName;
        this.title = title;
        this.subtitle = subtitle;
    }

    public String modelName() { return modelName; }
    public String registryName() { return "showcase_" + modelName; }
    public String title() { return title; }
    public String subtitle() { return subtitle; }
    public boolean isGalleryExhibit() { return this != ROCKET_ENGINE; }
}
