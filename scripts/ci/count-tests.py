#!/usr/bin/env python3
"""Cuenta las pruebas de los informes JUnit (XML) que deja Gradle en build/test-results/.

Uso: python3 scripts/ci/count-tests.py <resultados de logic/> <resultados de :app>

Escribe «Pruebas: N (lógica L, app A), fallidas F, omitidas S» en el registro y, en GitHub Actions, también en el
resumen de la ejecución ($GITHUB_STEP_SUMMARY). «Fallidas» suma los fallos y los errores. Solo informa: el paso
que ejecuta las pruebas es el que falla si alguna falla.
"""

import os
import sys
import xml.etree.ElementTree as ET
from pathlib import Path


def count(folder):
    """(pruebas, fallidas, omitidas) de todos los TEST-*.xml bajo `folder` (0 si no hay ninguno)."""
    tests = failed = skipped = 0
    for report in sorted(Path(folder).rglob("TEST-*.xml")):
        suite = ET.parse(report).getroot()
        tests += int(suite.get("tests", 0))
        failed += int(suite.get("failures", 0)) + int(suite.get("errors", 0))
        skipped += int(suite.get("skipped", 0))
    return tests, failed, skipped


def main():
    if len(sys.argv) != 3:
        sys.exit(__doc__.strip().splitlines()[2])
    logic = count(sys.argv[1])
    app = count(sys.argv[2])
    total = [a + b for a, b in zip(logic, app)]
    line = f"Pruebas: {total[0]} (lógica {logic[0]}, app {app[0]}), fallidas {total[1]}, omitidas {total[2]}"
    print(line)
    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary:
        with open(summary, "a", encoding="utf-8") as out:
            out.write(f"### {line}\n")


if __name__ == "__main__":
    main()
