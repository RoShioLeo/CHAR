// Halo console integration; administrative access is checked again by the server.
import { definePlugin } from '@halo-dev/ui-shared';
import DictionaryView from './DictionaryView.vue';

export default definePlugin({
    routes: [{
        parentName: 'ToolsRoot',
        route: {
            path: '/char-dictionary',
            name: 'CharDictionary',
            component: DictionaryView,
            meta: {
                title: '字库管理',
                permissions: ['plugin:lemonslice-char:manage'],
                menu: {name: '字库管理', group: 'tool', priority: 30}
            }
        }
    }]
});
