---
title: PMD Release Notes
permalink: pmd_release_notes.html
keywords: changelog, release notes
---

## {{ site.pmd.date | date: "%d-%B-%Y" }} - {{ site.pmd.version }}

The PMD team is pleased to announce PMD {{ site.pmd.version }}.

This is a {{ site.pmd.release_type }} release.

{% tocmaker is_release_notes_processor %}

### 🚀 New and noteworthy

### 🐛 Fixed Issues
* apex
  * [#5163](https://github.com/pmd/pmd/issues/5163): \[apex] Parser error when using toLabel in SOSL query
  * [#5182](https://github.com/pmd/pmd/issues/5182): \[apex] Parser error when using GROUPING in a SOQL query
* java-codestyle
  * [#5046](https://github.com/pmd/pmd/issues/5046): \[java] LocalVariableCouldBeFinal false positive with try/catch

### 🚨 API Changes

### ✨ External Contributions

{% endtocmaker %}

