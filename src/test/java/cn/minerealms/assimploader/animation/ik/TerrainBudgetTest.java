package cn.minerealms.assimploader.animation.ik;

import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TerrainBudgetTest {
    @Test void oversizedTerrainQueriesFailBeforeAnyWorldSampling() {
        var chain=List.of("hip","foot");
        assertThrows(IllegalArgumentException.class,()->new FootPlacementModifier(chain,null,32,33,0,1));
        assertThrows(IllegalArgumentException.class,()->new GroundBodyModifier("hip",List.of("foot"),1,Float.MAX_VALUE,1,0));
        assertThrows(IllegalArgumentException.class,()->new GroundBodyModifier("hip",List.of("foot"),1,1,65,0));
        assertThrows(IllegalArgumentException.class,()->new FootPlacementModifier(chain,null,1,1,Float.MAX_VALUE,1));
        var body=new GroundBodyModifier("hip",List.of("foot"),32,32,64,0);
        assertThrows(IllegalArgumentException.class,()->body.bind(new Matrix4f().scale(0),null));
        assertDoesNotThrow(()->body.bind(new Matrix4f(),null));
    }
}
