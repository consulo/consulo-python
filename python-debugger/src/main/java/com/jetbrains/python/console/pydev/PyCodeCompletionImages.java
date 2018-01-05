package com.jetbrains.python.console.pydev;

import javax.swing.Icon;

import org.jetbrains.annotations.Nullable;
import com.intellij.icons.AllIcons;

public class PyCodeCompletionImages {

    /**
     * Returns an image for the given type
     * @param type
     * @return
     */
    @Nullable
    public static Icon getImageForType(int type){
      switch (type) {
        case IToken.TYPE_CLASS:
          return AllIcons.Nodes.Class;
        case IToken.TYPE_FUNCTION:
          return AllIcons.Nodes.Method;
        default:
          return null;
      }
    }

}
