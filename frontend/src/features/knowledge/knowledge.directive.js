import angular from 'angular';
import template from './knowledge.html?raw';

angular.module('wxn.chats').directive('knowledgePage', function () {
  return {
    restrict: 'E',
    scope: {},
    controllerAs: 'vm',
    template,
    controller: [
      'ApiClientService',
      '$timeout',
      '$scope',
      function (api, $timeout, $scope) {
        const vm = this;
        const publishPath = (articleId, versionId) =>
          '/admin/knowledge/articles/' + articleId + '/versions/' + versionId + '/publish';
        let pollHandle = null;
        let selectionEpoch = 0;
        let previewEpoch = 0;
        vm.loading = true;
        vm.saving = false;
        vm.articles = [];
        vm.gaps = [];
        vm.gapPageNumber = 0;
        vm.gapPageSize = 20;
        vm.gapTotal = 0;
        vm.gapPageCount = 0;
        vm.form = {};
        vm.openCreate = () => {
          vm.closePublication();
          vm.createdDraft = null;
          vm.selected = null;
          vm.selectedArticleId = null;
          vm.selectedGapId = null;
          vm.formMode = 'create';
          vm.form = { title: '', category: '', content: '' };
          vm.formError = '';
          vm.formOpen = true;
        };
        vm.openEdit = (article) => {
          vm.closePublication();
          vm.createdDraft = null;
          vm.selectedGapId = null;
          vm.formMode = 'revision';
          vm.formOpen = true;
          vm.saving = true;
          vm.formError = '';
          api
            .get('/admin/knowledge/articles/' + article.id)
            .then((detail) => {
              if (vm.selectedArticleId !== article.id) {
                return;
              }
              vm.selected = detail.article;
              const latest = (detail.versions || []).reduce(
                (best, version) => (!best || version.number > best.number ? version : best),
                null,
              );
              vm.form = latest
                ? { title: latest.title, category: latest.category, content: latest.content }
                : { title: article.title, category: article.category, content: '' };
            })
            .catch((error) => {
              if (vm.selectedArticleId !== article.id) {
                return;
              }
              vm.formError =
                error.status === 404
                  ? 'Este artigo não está mais disponível. Atualize a lista.'
                  : error.userMessage || 'Não foi possível abrir o artigo.';
              if (error.status === 404) {
                vm.formOpen = false;
                vm.load();
              }
            })
            .finally(() => {
              if (vm.selectedArticleId === article.id) {
                vm.saving = false;
              }
            });
          vm.selectedArticleId = article.id;
        };
        vm.save = () => {
          if (vm.saving) {
            return;
          }
          vm.saving = true;
          vm.formError = '';
          const gapId = vm.formMode === 'gap' ? vm.selectedGapId : null;
          const path = gapId
            ? '/admin/knowledge/gaps/' + gapId + '/draft'
            : vm.selected
              ? '/admin/knowledge/articles/' + vm.selected.id + '/revisions'
              : '/admin/knowledge/articles';
          api
            .post(path, vm.form)
            .then((result) => {
              vm.formOpen = false;
              if (gapId) {
                vm.selectedGapId = null;
                vm.createdDraft = result.draft;
                return Promise.all([vm.load(), vm.loadGaps()]);
              }
              return vm.load();
            })
            .catch((error) => {
              vm.formError =
                error.status === 409
                  ? 'O artigo mudou ou foi arquivado. Atualize e tente novamente.'
                  : error.status === 404
                    ? 'O artigo não foi encontrado. Atualize a lista.'
                    : error.userMessage || 'Não foi possível salvar. Seus dados continuam no formulário.';
            })
            .finally(() => {
              vm.saving = false;
            });
        };
        vm.load = () => {
          vm.loading = true;
          vm.error = '';
          return api
            .get('/admin/knowledge/articles')
            .then((rows) => {
              vm.articles = rows || [];
            })
            .catch((error) => {
              vm.error = error.userMessage || 'Não foi possível carregar a Base de Conhecimento.';
            })
            .finally(() => {
              vm.loading = false;
            });
        };
        let gapEpoch = 0;
        vm.loadGaps = (page = vm.gapPageNumber) => {
          const epoch = ++gapEpoch;
          vm.gapLoading = true;
          vm.gapError = '';
          return api
            .get('/admin/knowledge/gaps?page=' + page + '&size=' + vm.gapPageSize)
            .then((result) => {
              if (epoch !== gapEpoch) {
                return;
              }
              vm.gaps = result.items || [];
              vm.gapPageNumber = result.page;
              vm.gapTotal = result.total;
              vm.gapPageCount = Math.ceil(result.total / vm.gapPageSize);
            })
            .catch((error) => {
              if (epoch === gapEpoch) {
                vm.gapError = error.userMessage || 'Não foi possível carregar as lacunas da base.';
              }
            })
            .finally(() => {
              if (epoch === gapEpoch) {
                vm.gapLoading = false;
              }
            });
        };
        vm.changeGapPage = (offset) => {
          const next = vm.gapPageNumber + offset;
          const last = Math.max(0, Math.ceil(vm.gapTotal / vm.gapPageSize) - 1);
          if (next >= 0 && next <= last && !vm.gapLoading) {
            vm.loadGaps(next);
          }
        };
        vm.openGapDraft = (gap) => {
          if (vm.saving || gap.status !== 'OPEN') {
            return;
          }
          vm.createdDraft = null;
          vm.formMode = 'gap';
          vm.selected = null;
          vm.selectedArticleId = null;
          vm.selectedGapId = gap.id;
          vm.form = { title: '', category: '', content: '' };
          vm.formError = '';
          vm.formOpen = true;
        };
        let gapDraftEpoch = 0;
        vm.viewGapDraft = (gap) => {
          if (!gap.draftArticleId) {
            return;
          }
          const epoch = ++gapDraftEpoch;
          vm.createdDraft = null;
          vm.gapDraftLoading = true;
          vm.gapDraftError = '';
          api
            .get('/admin/knowledge/articles/' + gap.draftArticleId)
            .then((detail) => {
              if (epoch === gapDraftEpoch) {
                vm.createdDraft = detail;
              }
            })
            .catch((error) => {
              if (epoch === gapDraftEpoch) {
                vm.gapDraftError = error.userMessage || 'Não foi possível abrir o rascunho.';
              }
            })
            .finally(() => {
              if (epoch === gapDraftEpoch) {
                vm.gapDraftLoading = false;
              }
            });
        };
        vm.closeCreatedDraft = () => {
          gapDraftEpoch += 1;
          vm.createdDraft = null;
          vm.gapDraftError = '';
        };
        vm.requestArchive = (article) => {
          vm.archiveCandidateId = article.id;
          vm.archiveError = '';
        };
        vm.cancelArchive = () => {
          vm.archiveCandidateId = null;
        };
        vm.archiveArticle = (article) => {
          if (vm.archivingId === article.id) {
            return;
          }
          vm.archivingId = article.id;
          vm.archiveError = '';
          api
            .post('/admin/knowledge/articles/' + article.id + '/archive', {})
            .then(() => {
              vm.archiveCandidateId = null;
              return vm.load();
            })
            .catch((error) => {
              vm.archiveError = error.userMessage || 'Não foi possível arquivar o artigo.';
            })
            .finally(() => {
              vm.archivingId = null;
            });
        };
        vm.closePublication = () => {
          selectionEpoch += 1;
          if (pollHandle) {
            $timeout.cancel(pollHandle);
            pollHandle = null;
          }
          vm.publication = null;
        };
        vm.publishDraft = (article) => {
          vm.closePublication();
          const epoch = selectionEpoch;
          vm.publication = { articleId: article.id, state: 'loading', message: 'Localizando a revisão…' };
          api
            .get('/admin/knowledge/articles/' + article.id)
            .then((detail) => {
              if (epoch !== selectionEpoch) {
                return null;
              }
              const latest = (detail.versions || []).reduce(
                (best, version) => (!best || version.number > best.number ? version : best),
                null,
              );
              if (!latest || latest.status !== 'DRAFT') {
                vm.publication = {
                  articleId: article.id,
                  state: 'error',
                  message: 'Não há uma revisão em rascunho pronta para publicar.',
                };
                return null;
              }
              vm.publication.versionId = latest.id;
              vm.publication.versionNumber = latest.number;
              return api.post(publishPath(article.id, latest.id), {}).then((status) => {
                if (epoch !== selectionEpoch) {
                  return null;
                }
                vm.applyPublicationStatus(article.id, latest.id, status, epoch);
                if (vm.publication && vm.publication.state === 'pending') {
                  vm.schedulePublicationPoll(article.id, latest.id, epoch);
                }
                return status;
              });
            })
            .catch((error) => {
              if (epoch !== selectionEpoch) {
                return;
              }
              vm.publication = {
                articleId: article.id,
                state: 'error',
                message:
                  error.status === 503
                    ? 'A indexação ainda não está habilitada. Tente novamente quando estiver disponível.'
                    : error.status === 409
                      ? 'A revisão mudou. Atualize a lista antes de tentar publicar.'
                      : error.userMessage || 'Não foi possível iniciar a publicação.',
              };
            });
        };
        vm.applyPublicationStatus = (articleId, versionId, status, epoch) => {
          if (epoch !== selectionEpoch || !vm.publication || vm.publication.articleId !== articleId) {
            return;
          }
          const state = status.status;
          const messages = {
            PENDING: 'Aguardando indexação…',
            PROCESSING: 'Indexando conteúdo…',
            COMPLETED: 'Artigo publicado.',
            FAILED: status.error || 'A indexação falhou após as tentativas disponíveis.',
          };
          vm.publication = {
            articleId,
            versionId,
            versionNumber: vm.publication.versionNumber,
            state: state === 'COMPLETED' ? 'published' : state === 'FAILED' ? 'failed' : 'pending',
            message: messages[state] || 'Consultando o estado da publicação…',
            terminal: state === 'COMPLETED' || state === 'FAILED',
          };
          if (state === 'COMPLETED') {
            vm.load();
            api
              .get('/admin/knowledge/articles/' + articleId)
              .then((detail) => {
                if (epoch === selectionEpoch && vm.publication && vm.publication.articleId === articleId) {
                  vm.publishedDetail = detail;
                }
              })
              .catch(() => {
                if (epoch === selectionEpoch && vm.publication && vm.publication.articleId === articleId) {
                  vm.publication.refreshMessage = 'Publicado. Não foi possível atualizar os dados do artigo.';
                }
              });
          }
        };
        vm.schedulePublicationPoll = (articleId, versionId, epoch) => {
          pollHandle = $timeout(() => {
            pollHandle = null;
            if (epoch !== selectionEpoch || !vm.publication || vm.publication.articleId !== articleId) {
              return;
            }
            api
              .get(publishPath(articleId, versionId))
              .then((status) => {
                if (epoch !== selectionEpoch) {
                  return;
                }
                vm.applyPublicationStatus(articleId, versionId, status, epoch);
                if (vm.publication && vm.publication.state === 'pending') {
                  vm.schedulePublicationPoll(articleId, versionId, epoch);
                }
              })
              .catch((error) => {
                if (epoch !== selectionEpoch) {
                  return;
                }
                vm.publication = {
                  articleId,
                  versionId,
                  state: 'error',
                  message: error.userMessage || 'Não foi possível consultar a indexação.',
                  canRefresh: true,
                };
              });
          }, 1500);
        };
        vm.refreshPublication = () => {
          if (!vm.publication || !vm.publication.versionId) {
            return;
          }
          const { articleId, versionId } = vm.publication;
          const epoch = selectionEpoch;
          vm.publication.state = 'loading';
          api
            .get(publishPath(articleId, versionId))
            .then((status) => {
              if (epoch !== selectionEpoch) {
                return;
              }
              vm.applyPublicationStatus(articleId, versionId, status, epoch);
              if (vm.publication && vm.publication.state === 'pending') {
                vm.schedulePublicationPoll(articleId, versionId, epoch);
              }
            })
            .catch((error) => {
              if (epoch === selectionEpoch && vm.publication) {
                vm.publication.state = 'error';
                vm.publication.message = error.userMessage || 'Não foi possível consultar a indexação.';
                vm.publication.canRefresh = true;
              }
            });
        };
        vm.testSearch = () => vm.requestPreview(false);
        vm.generateAnswerPreview = () => vm.requestPreview(true);
        vm.requestPreview = (generateAnswer) => {
          const question = (vm.previewQuestion || '').trim();
          if (!question || vm.previewLoading) {
            return;
          }
          const epoch = ++previewEpoch;
          vm.previewLoading = true;
          vm.previewGenerating = generateAnswer;
          vm.previewError = '';
          vm.previewResult = null;
          api
            .post(
              '/admin/knowledge/preview',
              generateAnswer ? { question, limit: 5, generate: true } : { question, limit: 5 },
            )
            .then((result) => {
              if (epoch !== previewEpoch || question !== (vm.previewQuestion || '').trim()) {
                return;
              }
              vm.previewResult = result;
            })
            .catch((error) => {
              if (epoch !== previewEpoch || question !== (vm.previewQuestion || '').trim()) {
                return;
              }
              vm.previewError = error.userMessage || 'Não foi possível testar a busca agora.';
            })
            .finally(() => {
              if (epoch === previewEpoch) {
                vm.previewLoading = false;
                vm.previewGenerating = false;
              }
            });
        };
        $scope.$watch('vm.previewQuestion', (question, previous) => {
          if (question === previous) {
            return;
          }
          previewEpoch += 1;
          vm.previewLoading = false;
          vm.previewGenerating = false;
          vm.previewResult = null;
          vm.previewError = '';
        });
        $scope.$on('$destroy', () => {
          vm.closePublication();
          previewEpoch += 1;
          gapEpoch += 1;
          gapDraftEpoch += 1;
        });
        vm.load();
        vm.loadGaps();
      },
    ],
  };
});
