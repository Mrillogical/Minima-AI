Analyze this URL for signs of phishing, scams, or malware based on its
structure alone - domain, TLD, subdomains, path keywords (e.g. 'login',
'verify', 'secure', 'free', 'account'), IP-based domains, excessive
subdomain nesting, or brand names combined with unrelated domains
(e.g. "paypal-verify-account.tk").

Calibration:
- github.com, google.com, well-known established domains -> LOW risk
- A generic blog or unfamiliar but structurally normal domain -> LOW or
  MEDIUM, not automatically HIGH just for being unfamiliar
- Brand name + suspicious TLD + urgency keyword together -> HIGH risk

Return: safe (true/false), riskLevel (LOW/MEDIUM/HIGH), and a one-sentence
reasoning that names the SPECIFIC signal you observed (e.g. "uses a
free .tk TLD combined with 'verify' in the path") - not a generic
statement like "this URL appears risky."

URL: {url}

