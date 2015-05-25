#!/bin/sh

echo '--------------------------------------------------------------'
echo 'Test Case 1: no geneSymbol'
./gbm_summarize.sh

echo '--------------------------------------------------------------'
echo 'Test Case 2: with one gene'
./gbm_summarize.sh TP53

echo '--------------------------------------------------------------'
echo 'Test Case 3: with multiple valid genes'
./gbm_summarize.sh TP53 MDM2 MDM4

echo '--------------------------------------------------------------'
echo 'Test Case 4: with a invalid gene in the mix'
./gbm_summarize.sh TP53 TP PTEN RB1

echo '--------------------------------------------------------------'
echo 'Test Case 5: with Glioblastoma TP53 Pathway (4 genes)'
./gbm_summarize.sh CDKN2A MDM2 MDM4 TP53

echo '--------------------------------------------------------------'
echo 'Test Case 6: with Glioblastoma RTK/Ras/PI3K/AKT Signaling (17 genes)'
./gbm_summarize.sh EGFR ERBB2 PDGFRA MET KRAS NRAS HRAS NF1 SPRY2 FOXO1 FOXO3 AKT1 AKT2 AKT3 PIK3R1 PIK3CA PTEN


