package cn.minerealms.rigworks.character.client;

import cn.minerealms.assimploader.util.ReadOnlyList;
import dev.latvian.mods.rhino.Context;
import dev.latvian.mods.rhino.ScriptableObject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ScriptCollectionReflectionTest {
    @Test void realRhinoCanReadImmutableListsAndCursorsWithoutOpeningJavaModules() {
        var context=Context.enter();var scope=context.initStandardObjects();
        var snapshot=ReadOnlyList.copyOf(List.of("first","second","third"));
        ScriptableObject.putProperty(scope,"snapshot",Context.javaToJS(context,snapshot,scope),context);
        var result=context.evaluateString(scope,"""
                if(snapshot.size() !== 3 || String(snapshot.get(1)) !== 'second') throw new Error('list methods');
                if(!snapshot.contains('first') || snapshot.subList(1,3).size() !== 2) throw new Error('collection methods');
                var cursor=snapshot.iterator();
                if(!cursor.hasNext() || String(cursor.next()) !== 'first') throw new Error('cursor methods');
                var refused=false;try{snapshot.clear()}catch(error){refused=true}
                if(!refused)throw new Error('mutation accepted');
                true;
                ""","collection.js",1,null);
        assertEquals(Boolean.TRUE,result);assertEquals(List.of("first","second","third"),snapshot);
        assertThrows(UnsupportedOperationException.class,()->snapshot.iterator().remove());
    }
}
