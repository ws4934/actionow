"use client";

import ReactCodeMirror, { type ReactCodeMirrorProps } from "@uiw/react-codemirror";
import { json } from "@codemirror/lang-json";
import { javascript } from "@codemirror/lang-javascript";
import { markdown } from "@codemirror/lang-markdown";
import { EditorView } from "@codemirror/view";
import { useMemo } from "react";

export type CodeLang = "json" | "groovy" | "markdown";

const FILL_THEME = EditorView.theme({
  "&": { height: "100%", width: "100%", fontSize: "13px" },
  ".cm-scroller": { overflow: "auto", fontFamily: '"JetBrains Mono", "Fira Code", "Cascadia Code", Menlo, monospace' },
  ".cm-content": { caretColor: "#aeafad" },
  ".cm-line": { lineHeight: "1.65" },
});

interface CodeEditorProps {
  value: string;
  onChange: (value: string) => void;
  lang: CodeLang;
  placeholder?: string;
  className?: string;
  style?: React.CSSProperties;
}

export function CodeEditor({ value, onChange, lang, placeholder, className, style }: CodeEditorProps) {
  const extensions = useMemo(() => {
    const base = [FILL_THEME, EditorView.lineWrapping];
    switch (lang) {
      case "json": return [...base, json()];
      case "groovy": return [...base, javascript()];
      case "markdown": return [...base, markdown()];
    }
  }, [lang]);

  return (
    <ReactCodeMirror
      value={value}
      onChange={onChange}
      theme="dark"
      extensions={extensions}
      placeholder={placeholder}
      height="100%"
      className={className}
      style={{ height: "100%", width: "100%", ...style }}
      basicSetup={{
        lineNumbers: true,
        foldGutter: false,
        dropCursor: false,
        allowMultipleSelections: false,
        indentOnInput: true,
        syntaxHighlighting: true,
        bracketMatching: true,
        closeBrackets: true,
        autocompletion: false,
        rectangularSelection: false,
        crosshairCursor: false,
        highlightActiveLine: true,
        highlightSelectionMatches: false,
        closeBracketsKeymap: true,
        searchKeymap: false,
        foldKeymap: false,
        completionKeymap: false,
        lintKeymap: false,
        tabSize: 2,
      }}
    />
  );
}
