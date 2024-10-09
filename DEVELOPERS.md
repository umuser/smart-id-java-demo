# Guidelines for developers of this repo

## Add a license header for each file

mvn license:update-file-header

## Keep 3rd party licenses file updated

If you add or remove dependencies then don't forget to run

mvn license:add-third-party

to update the file: LICENSE.3RD-PARTY 