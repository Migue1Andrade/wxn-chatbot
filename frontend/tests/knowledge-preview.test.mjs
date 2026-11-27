import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import vm from 'node:vm';

function createController() {
  let directiveFactory;
  const angular = {
    module: () => ({
      directive: (_name, factory) => {
        directiveFactory = factory;
      },
    }),
  };
  const source = readFileSync(
    new URL('../src/features/knowledge/knowledge.directive.js', import.meta.url),
    'utf8',
  ).replace(/^import .*;\n/gm, '');
  vm.runInNewContext(source, { angular, template: '' });
  const directive = directiveFactory();
  const requests = [];
  const watchers = {};
  const api = {
    get: (path) => Promise.resolve(path.includes('/gaps?') ? { items: [], page: 0, total: 0 } : []),
    post: (path, body) =>
      new Promise((resolve, reject) => {
        requests.push({ path, body, resolve, reject });
      }),
  };
  const scope = {
    $watch: (expression, callback) => {
      watchers[expression] = callback;
    },
    $on: () => {},
  };
  const vmController = {};
  const controller = directive.controller.at(-1);
  controller.call(vmController, api, () => {}, scope);
  return { vmController, requests, watchers };
}

test('search and answer generation are separate explicit requests', async () => {
  const { vmController, requests } = createController();
  vmController.previewQuestion = 'Pergunta fictícia';

  vmController.testSearch();
  requests[0].resolve({ generationStatus: 'NOT_REQUESTED', sources: [] });
  await new Promise((resolve) => setImmediate(resolve));
  vmController.generateAnswerPreview();

  assert.equal(requests.length, 2);
  assert.equal(requests[0].path, '/admin/knowledge/preview');
  assert.deepEqual(JSON.parse(JSON.stringify(requests[0].body)), {
    question: 'Pergunta fictícia',
    limit: 5,
  });
  assert.deepEqual(JSON.parse(JSON.stringify(requests[1].body)), {
    question: 'Pergunta fictícia',
    limit: 5,
    generate: true,
  });
});

test('question changes discard delayed generated previews', async () => {
  const { vmController, requests, watchers } = createController();
  vmController.previewQuestion = 'Pergunta antiga';
  vmController.generateAnswerPreview();
  vmController.previewQuestion = 'Pergunta atual';
  watchers['vm.previewQuestion']('Pergunta atual', 'Pergunta antiga');
  requests[0].resolve({
    generationStatus: 'GENERATED',
    generatedAnswer: 'Resposta atrasada',
    sources: [],
  });
  await new Promise((resolve) => setImmediate(resolve));

  assert.equal(vmController.previewResult, null);
  assert.equal(vmController.previewError, '');
  assert.equal(vmController.previewLoading, false);
});

test('generated preview text is interpolated as escaped plain text with Portuguese states', () => {
  const template = readFileSync(new URL('../src/features/knowledge/knowledge.html', import.meta.url), 'utf8');

  assert.match(template, /\{\{vm\.previewResult\.generatedAnswer\}\}/);
  assert.doesNotMatch(template, /ng-bind-html="vm\.previewResult\.generatedAnswer"/);
  assert.match(template, /gerador não foi chamado/);
  assert.match(template, /não está configurada neste ambiente/);
  assert.match(template, /Não foi possível gerar uma resposta agora/);
  assert.match(template, /continua[m]? disponíveis abaixo/);
});
