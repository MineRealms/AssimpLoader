package cn.minerealms.assimploader.nativeapi;

import cn.minerealms.assimploader.nativeapi.fabrik.FabriKNative;
import cn.minerealms.assimploader.nativeapi.fabrik.FabriKLibrary;
import com.sun.jna.Pointer;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class FabriKSignatureTest {
    @Test
    void libraryMirrorsTheFabriKHeader() throws Exception {
        String[] functions = {
                "fk_version", "fk_simd_backend",
                "fk_chain_create", "fk_chain_destroy", "fk_chain_bone_count",
                "fk_chain_set_bones", "fk_chain_get_bones", "fk_chain_set_joint",
                "fk_chain_set_base_constraint", "fk_chain_set_fixed_base", "fk_chain_set_params",
                "fk_chain_solve", "fk_chain_last_distance",
                "fk_batch_create", "fk_batch_destroy", "fk_batch_chain_count", "fk_batch_bone_count",
                "fk_batch_set_chain", "fk_batch_get_chain", "fk_batch_solve",
                "fk_chain2d_create", "fk_chain2d_destroy", "fk_chain2d_set_bones", "fk_chain2d_get_bones",
                "fk_chain2d_set_joint", "fk_chain2d_set_base_constraint", "fk_chain2d_solve",
        };
        for (String name : functions) {
            assertTrue(Arrays.stream(FabriKLibrary.class.getMethods()).anyMatch(m -> m.getName().equals(name)),
                    "missing JNA binding for " + name);
        }
        assertEquals(Pointer.class, FabriKLibrary.class.getMethod("fk_chain_create", int.class).getReturnType());
        assertEquals(float.class, FabriKLibrary.class.getMethod("fk_chain_solve",
                Pointer.class, float.class, float.class, float.class).getReturnType());
        Method batchSolve = FabriKLibrary.class.getMethod("fk_batch_solve", Pointer.class, float[].class, float[].class);
        assertEquals(void.class, batchSolve.getReturnType());
        assertEquals(void.class, FabriKLibrary.class.getMethod("fk_chain_set_fixed_base",
                Pointer.class, int.class, float[].class).getReturnType());
    }

    @Test
    void batchDistancesUsePaddedGroupsOfFour() {
        assertEquals(0, FabriKLibrary.paddedDistanceLength(0));
        assertEquals(4, FabriKLibrary.paddedDistanceLength(1));
        assertEquals(4, FabriKLibrary.paddedDistanceLength(4));
        assertEquals(8, FabriKLibrary.paddedDistanceLength(5));
    }

    @Test
    void nativeBindingsWorkWhenThePlatformIsCompatible() {
        assumeTrue(FabriKNative.available(), "FabriK native not loadable on this platform");
        FabriKLibrary lib = FabriKNative.library();
        assertNotNull(lib);
        assertNotNull(lib.fk_version());
        Pointer chain = lib.fk_chain_create(2);
        try {
            assertNotNull(chain);
            assertEquals(2, lib.fk_chain_bone_count(chain));
        } finally {
            lib.fk_chain_destroy(chain);
        }
    }
}
