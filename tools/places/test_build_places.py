"""
Tests for tools/places/build_places.py: the city-name clean-up rules, and the committed places.tsv against them.

Run: python3 -m unittest discover -s tools/places -p 'test_*.py' -v
"""

from __future__ import annotations

import csv
import re
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import build_places as bp  # noqa: E402

TSV = bp.DEFAULT_OUT


def rows() -> list[list[str]]:
    with open(TSV, encoding="utf-8", newline="") as f:
        return [r for r in csv.reader((line for line in f if not line.startswith("#")), delimiter="\t")]


class TidyCityTest(unittest.TestCase):
    def tidy(self, code: str, raw: str) -> tuple[str, list[str]]:
        return bp.tidy_city(code, raw)

    def test_strips_a_trailing_airport(self):
        self.assertEqual(self.tidy("EYK", "Beloyarskiy Airport"), ("Beloyarskiy", []))
        self.assertEqual(self.tidy("NLI", "Nikolayevsk-na-Amure Airport"), ("Nikolayevsk-na-Amure", []))

    def test_strips_an_island_suffix_after_a_dash(self):
        self.assertEqual(self.tidy("BEJ", "Tanjung Redeb - Borneo Island"), ("Tanjung Redeb", ["Borneo Island"]))
        self.assertEqual(self.tidy("TNJ", "Tanjung Pinang-Bintan Island"), ("Tanjung Pinang", ["Bintan Island"]))
        self.assertEqual(self.tidy("NTX", "Ranai-Natuna Besar Island"), ("Ranai", ["Natuna Besar Island"]))

    def test_keeps_islands_that_are_the_place(self):
        for raw in ["Hilton Head Island", "Wangi-wangi Island", "Lord Howe Island", "Grand Island"]:
            self.assertEqual(self.tidy("XXX", raw), (raw, []))

    def test_keeps_the_first_of_several_cities_and_the_rest_as_aliases(self):
        self.assertEqual(self.tidy("GTR", "Columbus/W Point/Starkville"), ("Columbus", ["W Point", "Starkville"]))
        self.assertEqual(self.tidy("RDU", "Raleigh/Durham"), ("Raleigh", ["Durham"]))
        self.assertEqual(self.tidy("KOK", "Kokkola / Kruunupyy"), ("Kokkola", ["Kruunupyy"]))

    def test_hand_fixes_win(self):
        self.assertEqual(self.tidy("CXR", "Nha Trang/nha Trang aiurportCam Ranh"), ("Nha Trang", ["Cam Ranh"]))
        self.assertEqual(self.tidy("YSQ", "Qian Gorlos Mongol Autonomous County")[0], "Songyuan")
        self.assertEqual(self.tidy("TRS", "Ronchi dei Legionari/Trieste")[0], "Trieste")

    def test_leaves_ordinary_names_alone(self):
        for raw in ["London", "São Paulo", "Wilkes-Barre", "Santiago de Compostela"]:
            self.assertEqual(self.tidy("XXX", raw), (raw, []))


class DatasetTest(unittest.TestCase):
    """The committed asset follows the rules (regenerate with build_places.py when this fails)."""

    def test_no_city_says_airport(self):
        bad = [(r[0], r[2]) for r in rows() if re.search(r"\bairport\b", r[2], re.IGNORECASE)]
        self.assertEqual(bad, [])

    def test_no_city_lists_several_places(self):
        self.assertEqual([(r[0], r[2]) for r in rows() if "/" in r[2]], [])

    def test_cities_are_short_unless_hand_picked(self):
        bad = [(r[0], r[2]) for r in rows() if len(r[2]) > bp.MAX_CITY_CHARS and r[0] not in bp.CITY_OVERRIDES]
        self.assertEqual(bad, [])


if __name__ == "__main__":
    unittest.main()
