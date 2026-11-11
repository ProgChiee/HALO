import Markdown from 'react-markdown';
import remarkBreaks from 'remark-breaks';

// Only explicit web links are clickable. No images or raw HTML from model output.
const components = {
  a: ({ href, children }) => href
    ? <a href={href} target="_blank" rel="noopener noreferrer">{children}</a>
    : <span>{children}</span>,
  img: ({ alt }) => <span>{alt}</span>,
};

export default function MentorMarkdown({ children }) {
  return <Markdown skipHtml remarkPlugins={[remarkBreaks]} components={components}
    urlTransform={url => /^https?:\/\//i.test(url) ? url : ''}>{children}</Markdown>;
}
