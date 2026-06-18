```json
{
  "title": "PeopleSoft 0-day affecting hundreds of organizations steals gigabytes of data",
  "author": "Dan Goodin",
  "site": "Ars Technica",
  "published": "2026-06-12T19:26:47+00:00"
}
```

One of the world’s most active ransomware groups exploited a critical vulnerability in Oracle’s PeopleSoft software suite and used it to target about 100 customers and extort at least one of them to pay up in exchange for not leaking stolen data, researchers said.

The group, tracked as ShinyHunters, had been exploiting the PeopleSoft vulnerability for more than two weeks before Oracle [flagged](https://blogs.oracle.com/security/security-alert-cve-2026-35273-released) it. CVE-2026-35273, as the vulnerability is tracked, carries a severity rating of 9.8 out of 10, making the former zero-day one of the year’s most critical vulnerabilities to be exploited.

Google’s Mandiant security team [said](https://cloud.google.com/blog/topics/threat-intelligence/shinyhunters-targets-education-sector-oracle-exploit) it’s an SSRF (server-side request forgery), a vulnerability that allows attackers to send requests from a susceptible server to systems used by the targeted organization. Oracle said the SSRF is remotely exploitable, and the company has issued a stopgap mitigation but has yet to fully patch the flaw. Google has confirmed that victims are receiving extortion demands.

## 9.8 0-day exploited for 2 weeks

The University of Nottingham [confirmed](https://www.nottingham.ac.uk/currentstudents/news/student-and-alumni-data-has-been-compromised-in-a-data-security-incident) on Wednesday that it was the victim of a hack that put a “significant” amount of student data in the hands of a threat actor. The confirmation came after ShinyHunters claimed the university was one of its recent victims and published gigabytes of data it claimed to have stolen in the hack.

Mandiant said ShinyHunters has been exploiting the vulnerability since May 27. As of Wednesday, the group had targeted roughly 300 endpoints belonging to 100 user organizations. About 68 percent of the organizations operated within the higher education sector. A researcher [said](https://infosec.exchange/@nahamike01@bird.makeup/116723358375518553) on Tuesday that the group responsible had “exposed several directories revealing ongoing targeting of PeopleSoft.” The attackers also left available a staging server containing tools used in the attack.

“While several organizations successfully blocked the activity or remediated the vulnerabilities, others experienced compromise, resulting in stolen data being published on the ShinyHunters DLS,” Mandiant said. (DLS is short for data leak site.)

An analysis of a bash script left in the staging environment shows the attackers performed reconnaissance on compromised organizations, including mapping the PeopleSoft configurations, viewing process scheduler, and WebLogic server XML configurations. Eventually, the threat actors established an outbound SSH connection to 176.120.22.24, the IP address hosting ShinyHunters’ DLS. The stolen data was first compressed using the zstd tool. The DLS claimed to have recovered 48GB of data from a single victim.

![](https://cdn.arstechnica.net/wp-content/uploads/2026/06/shinyhunters-dls.png)

*A partially redacted section of the ShinyHunters’ DLS. Credit: Mandiant*

ShinyHunters has been active since at least 2019. Over the past several years, it has executed scores of hacks against some of the world’s largest companies, affecting millions of people downstream. A small sample of victims includes Ticketmaster (through the breach of Snowflake, which hosted the data), Spain’s biggest bank, Santander, and [Salesforce](https://arstechnica.com/information-technology/2025/08/google-sales-data-breached-in-the-same-scam-it-discovered/) (and, through it, Google and, [reportedly](https://www.bleepingcomputer.com/news/security/google-suffers-data-breach-in-ongoing-salesforce-data-theft-attacks/), many other companies). ShinyHunters uses various techniques to gain initial access, including exploiting cloud misconfigurations and software vulnerabilities, stealing OAuth tokens, supply chain attacks, voice phishing, and other forms of social engineering.

Mandiant and [Rapid7](https://www.rapid7.com/blog/post/etr-active-exploitation-of-oracle-peoplesoft-zero-day-cve-2026-35273/) are providing detailed indicators of compromise. They are also advising PeopleSoft customers on the steps they should take immediately. Given ShinyHunters’ success rate, all PeopleSoft users would do well to heed the calls.
