package org.aohp.agentdriver.ui.widget;

import android.content.Context;
import android.graphics.Typeface;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.util.AttributeSet;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CodeViewer extends FrameLayout {
    private TextView lineNumbers;
    private TextView codeContent;
    private int lineNumberColor = 0xFF888888;
    private int codeColor = 0xFF000000;
    private int keywordColor = 0xFF0000FF; // Blue
    private int stringColor = 0xFF008800; // Green
    private int commentColor = 0xFF888888; // Gray

    public CodeViewer(Context context) {
        this(context, null);
    }

    public CodeViewer(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        // Layout Hierarchy:
        // ScrollView (Vertical)
        //   HorizontalScrollView (Horizontal)
        //     LinearLayout (Container)
        //       TextView (LineNums)
        //       TextView (Code)

        ScrollView verticalScroll = new ScrollView(getContext());
        verticalScroll.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        
        HorizontalScrollView horizontalScroll = new HorizontalScrollView(getContext());
        horizontalScroll.setLayoutParams(new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        
        LinearLayout container = new LinearLayout(getContext());
        container.setOrientation(LinearLayout.HORIZONTAL);
        
        lineNumbers = new TextView(getContext());
        lineNumbers.setGravity(Gravity.END);
        lineNumbers.setPadding(16, 16, 16, 16);
        lineNumbers.setBackgroundColor(0xFFEEEEEE);
        lineNumbers.setTextColor(lineNumberColor);
        lineNumbers.setTypeface(Typeface.MONOSPACE);
        lineNumbers.setTextSize(12);

        codeContent = new TextView(getContext());
        codeContent.setPadding(16, 16, 16, 16);
        codeContent.setTextColor(codeColor);
        codeContent.setTypeface(Typeface.MONOSPACE);
        codeContent.setTextSize(12);
        // codeContent.setTextIsSelectable(true); // Might interfere with scroll

        container.addView(lineNumbers);
        container.addView(codeContent);
        
        horizontalScroll.addView(container);
        verticalScroll.addView(horizontalScroll);
        
        addView(verticalScroll);
    }

    public void setCode(String code) {
        if (code == null) code = "";
        
        // 1. Line Numbers
        String[] lines = code.split("\n");
        StringBuilder sbNum = new StringBuilder();
        for (int i = 1; i <= lines.length; i++) {
            sbNum.append(i).append("\n");
        }
        lineNumbers.setText(sbNum.toString());

        // 2. Syntax Highlighting (Simple Regex)
        SpannableStringBuilder spannable = new SpannableStringBuilder(code);
        
        // Keywords (Python simplified)
        String[] keywords = {"def", "class", "if", "else", "elif", "return", "import", "from", "for", "while", "try", "except", "print", "True", "False", "None", "agent", "device", "ui", "data", "fm"};
        for (String keyword : keywords) {
            Pattern p = Pattern.compile("\\b" + keyword + "\\b");
            Matcher m = p.matcher(code);
            while (m.find()) {
                spannable.setSpan(new ForegroundColorSpan(keywordColor), m.start(), m.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        
        // Strings ("..." or '...')
        Pattern pString = Pattern.compile("\"([^\"]*)\"|'([^']*)'");
        Matcher mString = pString.matcher(code);
        while (mString.find()) {
            spannable.setSpan(new ForegroundColorSpan(stringColor), mString.start(), mString.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        // Comments (# ...)
        Pattern pComment = Pattern.compile("#.*");
        Matcher mComment = pComment.matcher(code);
        while (mComment.find()) {
            spannable.setSpan(new ForegroundColorSpan(commentColor), mComment.start(), mComment.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        codeContent.setText(spannable);
    }
}