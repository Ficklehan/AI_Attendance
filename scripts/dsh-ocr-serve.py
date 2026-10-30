#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Keep-alive JSONL wrapper around dsh-ocr-local PPOCRv5.

Loads ONNX once. Each stdin line is {"id":"...","path":"..."}.
Each stdout line is {"id":"...","lines":[...],"blocks":[...]} or {"id":"...","error":"..."}.
First stdout line is {"ok":true,"ready":true}.
"""
from __future__ import print_function

import argparse
import importlib.util
import json
import sys
from pathlib import Path


def load_ocr_module(ocr_script):
    path = Path(ocr_script)
    spec = importlib.util.spec_from_file_location("dsh_ocr_local_ocr", str(path))
    if spec is None or spec.loader is None:
        raise RuntimeError("cannot load ocr.py: " + str(path))
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


def main():
    parser = argparse.ArgumentParser(description="dsh-ocr-local keep-alive")
    parser.add_argument("--ocr-script", required=True, help="path to dsh-ocr-local/ocr/ocr.py")
    parser.add_argument("--model-dir", required=True, help="PP-OCRv5 model directory")
    args = parser.parse_args()

    mod = load_ocr_module(args.ocr_script)
    md = Path(args.model_dir)
    mod.DET_MODEL = md / "PP-OCRv5_mobile_det.onnx"
    mod.REC_MODEL = md / "PP-OCRv5_mobile_rec.onnx"
    mod.DICT_FILE = md / "ppocrv5_dict.txt"
    engine = mod.PPOCRv5()
    print(json.dumps({"ok": True, "ready": True, "engine": "ppocrv5"}), flush=True)

    for raw in sys.stdin:
        line = raw.strip()
        if not line:
            continue
        try:
            req = json.loads(line)
        except Exception as e:
            print(json.dumps({"id": "", "error": "bad json: " + str(e), "lines": []}), flush=True)
            continue
        req_id = req.get("id") or ""
        if req.get("cmd") == "quit":
            print(json.dumps({"id": req_id, "ok": True, "bye": True}), flush=True)
            break
        path = req.get("path") or ""
        try:
            lines, blocks = engine.ocr(path)
            print(json.dumps({"id": req_id, "lines": lines, "blocks": blocks}, ensure_ascii=False), flush=True)
        except Exception as e:
            print(json.dumps({"id": req_id, "error": str(e), "lines": []}, ensure_ascii=False), flush=True)


if __name__ == "__main__":
    main()
