const fs = require("fs");
const xml2js = require('xml2js');
let myArgs = process.argv.slice(2);

fs.readFile("pom.xml", "utf-8", (err, data) => {
  if (err) {
    throw err;
  }

  xml2js.parseString(data, (err, result) => {
    if (err) {
      throw err;
    }

    result.project.version = myArgs[0];

    const builder = new xml2js.Builder();
    const xml = builder.buildObject(result);

    fs.writeFile('pom.xml', xml, (err) => {
      if (err) {
        throw err;
      }
    });
  });
});