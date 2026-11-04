package com.example.data

object SampleData {

    data class SampleFile(
        val name: String,
        val format: String, // "RIS" or "CSV"
        val description: String,
        val content: String
    )

    val SAMPLES = listOf(
        SampleFile(
            name = "Pandemic_Genomics_Research.ris",
            format = "RIS",
            description = "8 References: 6 Open Access (PLOS, Nature Comms, eLife, BMC) & 2 Paywalled papers",
            content = """
TY  - JOUR
TI  - Deep learning for genomic epidemiology of infectious pathogens
AU  - Smith, Alexander J.
AU  - Robinson, Claire
AU  - Patel, Vikram
JO  - PLOS ONE
PY  - 2021
DO  - 10.1371/journal.pone.0245463
ER  - 

TY  - JOUR
TI  - Ultra-fast sequence alignment with high-throughput sequencing
AU  - Langmead, Ben
AU  - Salzberg, Steven L.
JO  - BMC Bioinformatics
PY  - 2020
DO  - 10.1186/s12859-020-03666-4
ER  - 

TY  - JOUR
TI  - Cryo-EM structure of the SARS-CoV-2 spike in the prefusion conformation
AU  - Wrapp, Daniel
AU  - Wang, Nianshuang
AU  - Corbett, Kizzmekia S.
JO  - Science
PY  - 2020
DO  - 10.1126/science.abb2507
ER  - 

TY  - JOUR
TI  - Global landscape of open-access scientific publications
AU  - Piwowar, Heather
AU  - Priem, Jason
AU  - Larivière, Vincent
JO  - Nature Communications
PY  - 2020
DO  - 10.1038/s41467-020-19818-2
ER  - 

TY  - JOUR
TI  - An open-source pipeline for whole-genome sequencing surveillance
AU  - Hadfield, James
AU  - Megill, Colin
AU  - Bell, Sidney M.
JO  - eLife
PY  - 2021
DO  - 10.7554/elife.65774
ER  - 

TY  - JOUR
TI  - Proprietary algorithms in commercial healthcare analytics
AU  - Obermeyer, Ziad
AU  - Powers, Brian
JO  - Closed Medical Journal
PY  - 2019
DO  - 10.1145/3065386
ER  - 

TY  - JOUR
TI  - Open science principles in modern viral pathogen sequencing
AU  - Andersen, Kristian G.
AU  - Rambaut, Andrew
JO  - Nature Medicine
PY  - 2020
DO  - 10.1038/s41591-020-0820-9
ER  - 

TY  - JOUR
TI  - Structural biology of emerging human coronaviruses
AU  - Walls, Alexandra C.
AU  - Veesler, David
JO  - Cell Press Exclusive
PY  - 2019
DO  - 10.1016/j.cell.2019.02.007
ER  - 
""".trimIndent()
        ),

        SampleFile(
            name = "AI_Deep_Learning_Papers.csv",
            format = "CSV",
            description = "7 Papers: Foundation AI transformer papers (arXiv & JMLR) plus paywalled benchmarks",
            content = """
Title,Authors,Year,Journal,DOI
"Attention Is All You Need","Vaswani, Ashish; Shazeer, Noam; Parmar, Niki",2017,"NeurIPS","10.48550/arxiv.1706.03762"
"Language Models are Few-Shot Learners","Brown, Tom B.; Mann, Benjamin; Ryder, Nick",2020,"arXiv","10.48550/arxiv.2005.14165"
"Deep Residual Learning for Image Recognition","He, Kaiming; Zhang, Xiangyu; Ren, Shaoqing",2016,"CVPR Open","10.48550/arxiv.1512.03385"
"Mastering the game of Go with deep neural networks","Silver, David; Huang, Aja; Maddison, Chris J.",2016,"Nature","10.1038/nature16961"
"Dropout: A Simple Way to Prevent Neural Networks from Overfitting","Srivastava, Nitish; Hinton, Geoffrey; Krizhevsky, Alex",2014,"JMLR","10.5555/2627435.2670313"
"Generative Adversarial Nets","Goodfellow, Ian; Pouget-Abadie, Jean; Mirza, Mehdi",2014,"NeurIPS","10.48550/arxiv.1406.2661"
"A Survey of Deep Learning in Healthcare","Ravì, Daniele; Wong, Charence; Deligianni, Fani",2017,"IEEE JBHI","10.1109/jbhi.2016.2636665"
""".trimIndent()
        ),

        SampleFile(
            name = "Open_Access_Biology_Collection.csv",
            format = "CSV",
            description = "5 Highly-cited open-access biology articles with 100% free legal PDFs",
            content = """
DOI,Title,Journal,Year
10.1371/journal.pone.0245463,"Deep learning for genomic epidemiology","PLOS ONE",2021
10.1186/s12859-020-03666-4,"Ultra-fast sequence alignment with high-throughput sequencing","BMC Bioinformatics",2020
10.1038/s41467-020-19818-2,"Global landscape of open-access scientific publications","Nature Communications",2020
10.7554/elife.65774,"An open-source pipeline for whole-genome sequencing surveillance","eLife",2021
10.48550/arxiv.1706.03762,"Attention Is All You Need","arXiv AI",2017
""".trimIndent()
        )
    )
}
