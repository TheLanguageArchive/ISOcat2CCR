ISOcat2CCR
==========
Tool to convert ISOcat cool URIs to CLARIN Concept Registry (CCR) handles.

```sh
java -jar src/target/isocat2ccr.jar mappings/ISOcat2CCR-full.csv resource.xml > resource-new.xml
```

See also

```sh
java -jar src/target/isocat2ccr.jar -?
```

for more options, e.g., to specify the encoding and line endings.

The mappings directory contains the following map files:

* ```ISOcat2CCR-full.csv```: from the official ISOcat cool URI and REST
  ISOcat URIs to the CCR handle
* ```ISOcat2CCR-CR.csv```: from ISOcat ConceptLinks using all kinds of
  correct and incorrect URIs to the CCR handle

You can easily add the URLs as they appear in your resource. Do notice
that not all ISOcat Data Categories have been taken along to the CCR.

Also note that the descending order of the mappings is important, i.e.,
the highest numbers have to be mapped first to prevent partial replacements.
