import {LitElement, html, css} from 'lit';

export class DemoTitle extends LitElement {

    static styles = css`
      h1 {
        font-family: "Titillium Web",sans-serif;
        font-size: 60px;
        font-style: normal;
        font-variant: normal;
        font-weight: 700;
        line-height: 26.4px;
        color: var(--main-highlight-text-color);
      }

      .title {
        text-align: center;
        padding: 1em;
      }
      
      .explanation {
        margin-left: auto;
        margin-right: auto;
        width: 50%;
        text-align: justify;
        font-size: 20px;
        color: var(--main-highlight-text-color);
      }
      
      .explanation img {
        max-width: 60%;
        display: block;
        float:left;
        margin-right: 2em;
        margin-top: 1em;
      }
    `

    render() {
        return html`
            <div class="title">
                <h1>WildFly Chat Bot</h1>
            </div>
            <div class="explanation">
                This bot can connect to your WildFly servers and help you monitor  the server.
                
                Suggested questions that you can try:
                <ul>
                    <li>Hi, could you connect to the WildFly server and get its status?</li>
                    <li>
                </ul>
            </div>
        `
    }


}

customElements.define('demo-title', DemoTitle);