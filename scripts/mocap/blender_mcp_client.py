"""Call the official Blender MCP server tools through the MCP Python SDK."""
import argparse
import asyncio
import base64
import json
import logging
import os
from datetime import timedelta
from pathlib import Path

async def main():
    p = argparse.ArgumentParser()
    p.add_argument('--port', type=int, default=9877)
    p.add_argument('--tool', default='execute_blender_code')
    p.add_argument('--code-file', type=Path)
    p.add_argument('--args-file', type=Path)
    p.add_argument('--image-out', type=Path)
    p.add_argument('--timeout', type=float, default=180)
    args = p.parse_args()
    os.environ.update(BLENDER_HOST='127.0.0.1', BLENDER_PORT=str(args.port),
                      DISABLE_TELEMETRY='true', PYTHONIOENCODING='utf-8')
    from mcp.shared.memory import create_connected_server_and_client_session
    from blender_mcp.server import mcp
    logging.getLogger('BlenderMCPServer').setLevel(logging.WARNING)
    arguments = {'user_prompt': '查看这个镰刀攻击，视频播放可以0.5倍速不然太快，使用谷歌的计算机视觉动捕，然后重定向到herobrine方块人模型，连接blender mcp'}
    if args.code_file:
        arguments['code'] = '__file__ = ' + repr(str(args.code_file.resolve())) + '\n' + args.code_file.read_text(encoding='utf-8')
    elif args.args_file:
        arguments.update(json.loads(args.args_file.read_text(encoding='utf-8')))
    elif args.tool == 'execute_blender_code':
        arguments['code'] = "import bpy,os,json;print(json.dumps({'pid':os.getpid(),'file':bpy.data.filepath,'scene':bpy.context.scene.name,'rigs':[o.name for o in bpy.data.objects if o.type=='ARMATURE']},ensure_ascii=True))"
    async with create_connected_server_and_client_session(mcp, read_timeout_seconds=timedelta(seconds=args.timeout)) as session:
        if args.tool == 'list_tools':
            result = await session.list_tools()
            print(result.model_dump_json(indent=2))
            return
        result = await session.call_tool(args.tool, arguments, read_timeout_seconds=timedelta(seconds=args.timeout))
        print('MCP_SERVER: official blender-mcp, MCP SDK transport, localhost:' + str(args.port))
        for content in result.content:
            if content.type == 'text':
                print(content.text)
                if content.text.startswith(('Error executing code:', 'Error getting scene info:')):
                    raise RuntimeError(content.text)
            elif content.type == 'image' and args.image_out:
                args.image_out.parent.mkdir(parents=True, exist_ok=True)
                args.image_out.write_bytes(base64.b64decode(content.data))
                print('MCP_SCREENSHOT', str(args.image_out))
        if result.isError:
            raise RuntimeError('Blender MCP tool returned an error')

if __name__ == '__main__':
    asyncio.run(main())
