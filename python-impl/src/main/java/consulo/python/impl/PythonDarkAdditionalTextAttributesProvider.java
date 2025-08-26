package consulo.python.impl;

import com.jetbrains.python.impl.highlighting.PyHighlighter;
import consulo.annotation.component.ExtensionImpl;
import consulo.colorScheme.AttributesFlyweightBuilder;
import consulo.colorScheme.EditorColorSchemeExtender;
import consulo.colorScheme.EditorColorsScheme;
import consulo.colorScheme.EffectType;
import consulo.ui.color.RGBColor;
import jakarta.annotation.Nonnull;

/**
 * @author VISTALL
 * @since 21/06/2023
 */
@ExtensionImpl
public class PythonDarkAdditionalTextAttributesProvider implements EditorColorSchemeExtender {
    @Nonnull
    @Override
    public String getColorSchemeId() {
        return EditorColorsScheme.DARCULA_SCHEME_NAME;
    }

    @Override
    public void extend(Builder builder) {
        builder.add(PyHighlighter.PY_BUILTIN_NAME, AttributesFlyweightBuilder.create()
            .withForeground(new RGBColor(0x80, 0x00, 0x0))
            .build());

        builder.add(PyHighlighter.PY_CLASS_DEFINITION, AttributesFlyweightBuilder.create()
            .withBoldFont()
            .withEffect(EffectType.WAVE_UNDERSCORE, null)
            .build());

        builder.add(PyHighlighter.PY_DECORATOR, AttributesFlyweightBuilder.create()
            .withForeground(new RGBColor(0xbb, 0xb5, 0x29))
            .withEffect(EffectType.WAVE_UNDERSCORE, null)
            .build());

        builder.add(PyHighlighter.PY_FUNC_DEFINITION, AttributesFlyweightBuilder.create()
            .withBoldFont()
            .withEffect(EffectType.WAVE_UNDERSCORE, null)
            .build());

        builder.add(PyHighlighter.PY_KEYWORD_ARGUMENT, AttributesFlyweightBuilder.create()
            .withForeground(new RGBColor(0xaa, 0x49, 0x26))
            .build());

        builder.add(PyHighlighter.PY_PREDEFINED_DEFINITION, AttributesFlyweightBuilder.create()
            .withForeground(new RGBColor(0xB2, 0x00, 0xB2))
            .build());

        builder.add(PyHighlighter.PY_PREDEFINED_USAGE, AttributesFlyweightBuilder.create()
            .withForeground(new RGBColor(0xB2, 0x00, 0xB2))
            .build());

        builder.add(PyHighlighter.PY_SELF_PARAMETER, AttributesFlyweightBuilder.create()
            .withForeground(new RGBColor(0x94, 0x55, 0x8D))
            .build());

        builder.add(PyHighlighter.PY_BYTE_STRING, AttributesFlyweightBuilder.create()
            .withForeground(new RGBColor(0xa5, 0xc2, 0x61))
            .withBoldFont()
            .build());

        builder.add(PyHighlighter.PY_UNICODE_STRING, AttributesFlyweightBuilder.create()
            .withForeground(new RGBColor(0x00, 0x80, 0x80))
            .withBoldFont()
            .build());
    }
}
