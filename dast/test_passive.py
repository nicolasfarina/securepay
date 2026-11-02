import unittest
from passive import sanitized_alerts, target_url


class SafetyTests(unittest.TestCase):
    def test_rejects_external_and_ambiguous_paths(self):
        for path in ("https://example.com", "//example.com", "/../admin", "/a?url=https://x",
                     "/a#b", "/%2f%2fx", "/a\\b", "/a\r\nHost:x"):
            with self.subTest(path=path), self.assertRaises(ValueError):
                target_url(path)

    def test_accepts_fixed_local_path(self):
        self.assertEqual(target_url("/api/payments/abc-123"), "http://api:8080/api/payments/abc-123")

    def test_report_omits_credentials_and_evidence(self):
        alert = {"alert": "header missing", "risk": "Low", "url": "http://api:8080/auth/login",
                 "requestHeader": "Authorization: Bearer private", "evidence": "private",
                 "attack": "private", "responseBody": "private"}
        sanitized = sanitized_alerts([alert])[0]
        self.assertEqual(sanitized["risk"], "Low")
        self.assertFalse(set(sanitized) & {"requestHeader", "evidence", "attack", "responseBody"})


if __name__ == "__main__":
    unittest.main()
