import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {parse} from 'acorn';

test('the Legacy entry point parses as strict ECMAScript 5 for WebKit 534',()=>{
 const source=readFileSync(new URL('../../platforms/samsung-legacy/web/app.js',import.meta.url),'utf8');
 assert.doesNotThrow(()=>parse(source,{ecmaVersion:5}));
});
