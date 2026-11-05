const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const ts = require('typescript');

function compile(file, dependencies = {}) {
  const module = { exports: {} };
  const javascript = ts.transpileModule(fs.readFileSync(path.join(__dirname, file), 'utf8'), {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022, experimentalDecorators: true }
  }).outputText;
  vm.runInNewContext(javascript, {
    module, exports: module.exports,
    require: name => Object.hasOwn(dependencies, name) ? dependencies[name] : require(name)
  });
  return module.exports;
}

const model = compile('../model/account.model.ts');
const { AccountComponent } = compile('account.component.ts', {
  '@angular/core': { Component: () => component => component },
  '../model/account.model': model,
  './button-renderer.component': { ButtonCellRendererComponent: class {} }
});

function formatters() {
  const component = new AccountComponent({});
  return [
    component.columnDefs.find(column => column.field === 'id').valueFormatter,
    component.columnDefsUser.find(column => column.field === 'accountId').valueFormatter
  ];
}

test('account and user grids show the masked account number, never the opaque routing key', () => {
  for (const format of formatters()) {
    assert.equal(format({
      value: 'a7a8e4a5-272b-4af8-846b-8021a282917c', data: { accountNumber: '****2214' }
    }), '****2214');
  }
});

test('legacy numeric account values remain masked and missing values stay empty', () => {
  for (const format of formatters()) {
    assert.equal(format({ value: 22214 }), '****2214');
    assert.equal(format({ value: null }), '');
    assert.equal(format({ value: undefined }), '');
  }
});
