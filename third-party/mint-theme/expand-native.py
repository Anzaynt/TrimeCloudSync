import ctypes as c,pathlib,os,shutil
base=pathlib.Path('work/mint-theme/native').resolve();user=base/'user';shared=base/'shared';logs=base/'logs'
for p in [user,shared,logs]:p.mkdir(parents=True,exist_ok=True)
root=pathlib.Path('work/trime-integrated/source/trime-af23c4a631274d118ed76ae77e6dc8d8f7024c56')
shutil.copyfile(root/'app/src/main/assets/shared/trime.yaml',shared/'trime.yaml')
(shared/'danjing.yaml').write_text(pathlib.Path('work/mint-theme/danjing.yaml').read_text(encoding='utf-8').replace('单静.trime:', 'mint.trime:'),encoding='utf-8')
source=pathlib.Path('work/mint-theme/upstream/ThemeForTrime/mint.trime.yaml').read_text(encoding='utf-8')
for key in ['conf','styl','preset_keys']:source=source.replace(key+':\n',key+':\n  __include: danjing:/'+key+'\n',1)
(user/'mint.trime.yaml').write_text(source,encoding='utf-8')
class Traits(c.Structure):
 _fields_=[('data_size',c.c_int)]+[(n,c.c_char_p) for n in ('shared_data_dir','user_data_dir','distribution_name','distribution_code_name','distribution_version','app_name')]+[('modules',c.POINTER(c.c_char_p)),('min_log_level',c.c_int)]+[(n,c.c_char_p) for n in ('log_dir','prebuilt_data_dir','staging_dir')]
names=['setup','set_notification_handler','initialize','finalize','start_maintenance','is_maintenance_mode','join_maintenance_thread','deployer_initialize','prebuild','deploy','deploy_schema','deploy_config_file','sync_user_data']
class Api(c.Structure):_fields_=[('data_size',c.c_int)]+[(n,c.c_void_p) for n in names]
keep=os.add_dll_directory('F:/App/wealse/weasel-0.17.4');lib=c.CDLL('F:/App/wealse/weasel-0.17.4/rime.dll');lib.rime_get_api.restype=c.POINTER(Api);api=lib.rime_get_api().contents
t=Traits();t.data_size=c.sizeof(t)-c.sizeof(c.c_int)
for n,p in [('shared_data_dir',shared),('user_data_dir',user),('log_dir',logs)]:setattr(t,n,str(p).encode('utf-8'))
t.distribution_name=b'Mint Theme Test';t.distribution_code_name=b'mint-test';t.distribution_version=b'0.2.4';t.app_name=b'rime.mint-test';t.min_log_level=2
c.CFUNCTYPE(None,c.POINTER(Traits))(api.setup)(c.byref(t));c.CFUNCTYPE(None,c.POINTER(Traits))(api.initialize)(c.byref(t))
c.CFUNCTYPE(None,c.POINTER(Traits))(api.deployer_initialize)(c.byref(t))
try:
 ok=c.CFUNCTYPE(c.c_int,c.c_char_p,c.c_char_p)(api.deploy_config_file)(b'mint.trime.yaml',b'config_version')
 assert ok,'Native config compile failed'
 f=user/'build/mint.trime.yaml';assert f.exists();print('Expanded Mint theme:',f.stat().st_size)
finally:c.CFUNCTYPE(None)(api.finalize)()
